package lb;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.IOException;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

public class LoadBalancer {
	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int MSSserverPort = 8001;

	//LoadBalancer, this
	private static final String LBServerAddress = "0.0.0.0"; //localhost because they are running on the same machine
	private static final int LBServerPort = 8000;

	//Maps total blockCount (per thread) to instance id
	private static Map<String, Integer> BlockCountTotalMap = new HashMap<String, Integer>();

	//Maps list with each blockCount to instance id
	private static Map<String, ArrayList<Integer>> BlockCountSeparateMap = new HashMap<String, ArrayList<Integer>>();

    public static void main(final String[] args) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(LBServerAddress, LBServerPort), 0);

		updateInstanceStates("54.226.205.197", 0);

		server.createContext("/scan", new LBToWebserverHandler());
		server.createContext("/getAverageBlockCount", new GetAverageBlockCount());
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
    }

	private static class GetAverageBlockCount implements HttpHandler {
		@Override
		public void handle(final HttpExchange exchange) throws IOException {
			int result = 0;
			for (int blockCount : BlockCountTotalMap.values()) {
				result += blockCount;
			}
			result = result/BlockCountTotalMap.size();
			String average = ""+result;
			
			byte[] response = average.getBytes();
        	exchange.sendResponseHeaders(200, response.length);
        	OutputStream os = exchange.getResponseBody();
        	os.write(response);
        	os.close();
		}
	}

	private static class LBToWebserverHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange exchange) throws IOException {
			//The LB gets the request here
			final String query = exchange.getRequestURI().getQuery();
			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			final ArrayList<String> args = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				args.add(splitParam[1]);
			}

			String scan_type = args.get(8);
		
			int xMin = Integer.parseInt(args.get(2)); int xMax = Integer.parseInt(args.get(3));
			int yMin = Integer.parseInt(args.get(4)); int yMax = Integer.parseInt(args.get(5));
			int area = (xMax-xMin) * (yMax-yMin);

			System.out.println("LB - Area: "+area);

			//Then checks the MSS for an estimate for this query
			int estimate = getEstimateMetric(scan_type, area);
			
			//Overfit an estimate for when we don't have values in the MSS
			if (estimate == -1) estimate = 100 * area;
			System.out.println("LB - Estimate: "+estimate);
	
			//Then chooses the best instance to send
			String chosenInstanceIP = chooseBestInstance();

			//Then updates ICountTotal and ICountSeparate +estimate
			updateInstanceStates(chosenInstanceIP, estimate);
			getInstanceStates();

			//Then sends the request it got to the chosen instance
			HttpURLConnection connection = sendRequestToWebServer(chosenInstanceIP, query);

			//Then waits for the request to come back from the instance, fault tolerance here
			int block_count = Integer.parseInt(connection.getHeaderField("Block-Count"));
			System.out.println("WebServer - Block Count: "+block_count);

			storeMetric(scan_type, area, block_count);

			//Then updates the ICountTotal and ICountSeparate -estimate
			//updateInstanceStates(chosenInstanceIP, estimate);
			updateInstanceStates(chosenInstanceIP, -estimate);
			getInstanceStates();

			//Then returns the results back to the user
			final Headers hdrs = exchange.getResponseHeaders();

			hdrs.add("Content-Type", "image/png");
			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
			InputStream in = connection.getInputStream();
        	OutputStream os = exchange.getResponseBody();

			exchange.sendResponseHeaders(200, connection.getContentLength());

			try {
				byte[] buf = new byte[8192];
				int length;
				while ((length = in.read(buf)) > 0) {
					os.write(buf, 0, length);
				}
			} catch (Exception e) {
				e.printStackTrace();
			}
			System.out.println("WebServer - Finished reading.");

			os.close();
			in.close();
			connection.disconnect();
			System.out.println("WebServer - Connection and Streams closed.");
			
            System.out.println("> Sent response to " + exchange.getRemoteAddress().toString());
		}
	}

	private static void updateInstanceStates(String instanceIP, int blockCount) {
		if (BlockCountSeparateMap.get(instanceIP) == null) { //Initialize the lists
			BlockCountTotalMap.put(instanceIP, 0);
			BlockCountSeparateMap.put(instanceIP, new ArrayList<Integer>());
		}

		boolean contains = true;
		if (blockCount > 0) { //Add blockCount to list, we are adding a job to the instance	
			BlockCountSeparateMap.get(instanceIP).add(blockCount);
		} else if (blockCount < 0) { //Remove blockCount from list, the instance has finished the job
			contains = BlockCountSeparateMap.get(instanceIP).remove(Integer.valueOf(-blockCount));
		}
		//If we try to remove a value that doesnt exist, we don't update the total
		if (contains) BlockCountTotalMap.put(instanceIP, BlockCountTotalMap.get(instanceIP) + blockCount);
	}

	private static void getInstanceStates() {
		System.out.println("---------------Instance States---------------");
		for (String instanceIP : BlockCountTotalMap.keySet()) {
			System.out.println("LB - "+instanceIP+" has "+BlockCountTotalMap.get(instanceIP)+" estimate blockCount total.");
			for (int separateICount : BlockCountSeparateMap.get(instanceIP)) {
				System.out.println("   -perThread has "+separateICount);
			}
		}
	}

	private static String chooseBestInstance() {
		//TODO update this to maybe take into account if the instance has a big job vs many small ones
		
		String bestInstanceIP = null;
		int minBlockCount = -1;
		for (String instanceIP : BlockCountTotalMap.keySet()) {
			int blockCount = BlockCountTotalMap.get(instanceIP);
			if (blockCount < minBlockCount || minBlockCount == -1) {
				bestInstanceIP = instanceIP;
				minBlockCount = blockCount;
			}
		}
		return bestInstanceIP;
	}

	private static int getEstimateMetric(String scan_type, int area) throws IOException {
		URL url = new URL("http://"+MSSserverAddress+":"+MSSserverPort+"/getEstimateMetric?scanType="+scan_type+"&area="+area);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("MSS - Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine(); //1st line is descriptive
		int result = Integer.parseInt(in.readLine()); //2nd line is actual result
		in.close();

		System.out.println("MSS - Finished reading: "+response);

		connection.disconnect();
		System.out.println("MSS - Connection closed.");

		return result;
	}

	private static void storeMetric(String scan_type, int area, int bcount) throws IOException {
		URL url = new URL("http://"+MSSserverAddress+":"+MSSserverPort+"/storeMetric?scanType="+scan_type+"&area="+area+"&bcount="+bcount);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("MSS - Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine(); //1st line is descriptive
		in.close();

		System.out.println("MSS - Finished writing: "+response);

		connection.disconnect();
		System.out.println("MSS - Connection closed.");
	}

	private static HttpURLConnection sendRequestToWebServer(String instanceIP, String query) throws IOException{
		int serverPort = 8000;

		URL url = new URL("http://"+instanceIP+":"+serverPort+"/scan?"+query);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("WebServer - Status: "+status);

		return connection;
	}

	private static HttpURLConnection sendRequestToWebServerLocal(int instancePort, String query) throws IOException{
		String serverIP = "127.0.0.1";

		URL url = new URL("http://"+serverIP+":"+instancePort+"/scan?"+query);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("WebServer - Status: "+status);

		return connection;
	}
}