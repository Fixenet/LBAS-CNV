package as;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.HttpURLConnection;

public class AutoScaler {
	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int MSSserverPort = 8001;
	//LoadBalancer
	private static final String LBserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int LBserverPort = 8000;
    public static void main(final String[] args) throws IOException {
		System.out.println(getAverageInstructionCount());
    }
	
	private static int getAverageInstructionCount() throws IOException {
		URL url = new URL("http://"+LBserverAddress+":"+LBserverPort+"/getAverageInstructionCount");
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		int result = Integer.parseInt(in.readLine());
		in.close();

		System.out.println("Finished reading: AverageInstructionCount = "+result);

		connection.disconnect();
		System.out.println("Connection closed.");

		return result;
	}

	private static void manageActiveInstances(String instanceIP, String mode) throws IOException {
		URL url = new URL("http://"+LBserverAddress+":"+LBserverPort+"/manageActiveInstances?instanceIP="+instanceIP+"&mode="+mode);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine();
		in.close();

		System.out.println("Finished reading: "+response);

		connection.disconnect();
		System.out.println("Connection closed.");
	}
}