package as;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

import java.net.URL;
import java.net.HttpURLConnection;

public class AutoScaler {
	//LoadBalancer
	private static final String LBserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
	private static final int LBserverPort = 8001;
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

		System.out.println("Finished reading: "+result);

		connection.disconnect();
		System.out.println("Connection closed.");

		return result;
	}
}