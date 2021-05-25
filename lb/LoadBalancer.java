package lb;

import java.io.BufferedReader;
import java.io.File;
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

import java.nio.file.Files;
import java.net.URL;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;

public class LoadBalancer {
	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int MSSserverPort = 8050;

	//LoadBalancer, this
	private static final String serverAddress = "0.0.0.0"; //localhost because they are running on the same machine
	private static final int serverPort = 8001;

	//Maps total icount (per thread) to instance id
	private static Map<String, Integer> ICountTotalMap = new HashMap<String, Integer>();

	//Maps list with each icount to instance id
	private static Map<String, ArrayList<Integer>> ICountSeparateMap = new HashMap<String, ArrayList<Integer>>();

    public static void main(final String[] args) throws IOException {
		updateInstanceStates("ID 1", 1000);
		getInstanceStates();

		updateInstanceStates("ID 1", 4000);
		updateInstanceStates("ID 1", 4000);
		updateInstanceStates("ID 1", 4000);
		getInstanceStates();

		updateInstanceStates("ID 2", 1000);
		getInstanceStates();

		updateInstanceStates("ID 1", -4000);
		getInstanceStates();

		updateInstanceStates("ID 1", -2000);
		getInstanceStates();

		updateInstanceStates("ID 1", -1000);
		getInstanceStates();

        final HttpServer server = HttpServer.create(new InetSocketAddress(serverAddress, serverPort), 0);

		server.createContext("/scan", new LBToWebserverHandler());
		server.createContext("/getAverageInstructionCount", new GetAverageInstructionCount());
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
    }

	private static class GetAverageInstructionCount implements HttpHandler {
		@Override
		public void handle(final HttpExchange exchange) throws IOException {
			int result = 0;
			for (int iCount : ICountTotalMap.values()) {
				result += iCount;
			}
			result = result/ICountTotalMap.size();
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

			//Then checks the MSS for an estimate for this query

			//Then sees the status of each instance

			//Then chooses the best instance to send

			//Then sends the request it got to the chosen instance
			String chosenInstanceIP = "127.0.0.1";
			InputStream in = sendRequestToWebServer(chosenInstanceIP, query);

			//Then waits for the request to come back from the instance

			//Then returns the results back to the user
			final Headers hdrs = exchange.getResponseHeaders();

			hdrs.add("Content-Type", "image/png");
			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
        	exchange.sendResponseHeaders(200, in.available());
        	OutputStream os = exchange.getResponseBody();

			File responseFile = new File("./tmp/result.png");

			Files.copy(in, responseFile.toPath());
			Files.copy(responseFile.toPath(), os);

			in.close();
        	os.close();
			
            System.out.println("> Sent response to " + exchange.getRemoteAddress().toString());
		}
	}

	private static void updateInstanceStates(String instanceID, int iCount) {
		if (ICountSeparateMap.get(instanceID) == null) { //Initialize the lists
			ICountTotalMap.put(instanceID, 0);
			ICountSeparateMap.put(instanceID, new ArrayList<Integer>());
		}

		boolean contains = true;
		if (iCount > 0) { //Add iCount to list, we are adding a job to the instance	
			ICountSeparateMap.get(instanceID).add(iCount);
		} else { //Remove iCount from list, the instance has finished the job
			contains = ICountSeparateMap.get(instanceID).remove(Integer.valueOf(-iCount));
		}
		//If we try to remove a value that doesnt exist, we don't update the total
		if (contains) ICountTotalMap.put(instanceID, ICountTotalMap.get(instanceID) + iCount);
	}

	private static void getInstanceStates() {
		System.out.println("---------------");
		for (String instanceID : ICountTotalMap.keySet()) {
			System.out.println(instanceID+" has "+ICountTotalMap.get(instanceID));
			for (int separateICount : ICountSeparateMap.get(instanceID)) {
				System.out.println("   -perThread has "+separateICount);
			}
		}
	}

	private static String chooseBestInstance() {
		return "BestID";
	}

	private static int getEstimateMetric(String scan_type, int area) throws IOException {

		URL url = new URL("http://"+serverAddress+":"+serverPort+"/getEstimateMetric?st="+scan_type+"&area="+area);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine(); //1st line is descriptive
		int result = Integer.parseInt(in.readLine()); //2nd line is actual result
		in.close();

		System.out.println("Finished reading: "+response);

		connection.disconnect();
		System.out.println("Connection closed.");

		return result;
	}

	private static InputStream sendRequestToWebServer(String instanceIP, String query) throws IOException{
		int serverPort = 8000;

		URL url = new URL("http://"+instanceIP+":"+serverPort+"/scan?"+query);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		InputStream in = connection.getInputStream();

		System.out.println("Finished reading");

		connection.disconnect();
		System.out.println("Connection closed.");

		return in;
	}
}