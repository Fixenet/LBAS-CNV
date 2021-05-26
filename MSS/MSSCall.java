package MSS;

import java.net.URL;
import java.net.HttpURLConnection;

import java.util.Map;
import java.util.HashMap;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

public class MSSCall {
	private static final String serverAddress = "127.0.0.1"; //localhost for now
	private static final int serverPort = 8001;
    public static void main(final String[] args) throws IOException {
		String st = "GRID";
		int area = 12;

		System.out.println("Result: "+getEstimateMetric(st, area));

		int icount = 7000;
		area = 8;
		storeMetric(st, area, icount);
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

	private static void storeMetric(String scan_type, int area, int icount) throws IOException {
		URL url = new URL("http://"+serverAddress+":"+serverPort+"/storeMetric?st="+scan_type+"&area="+area+"&icount="+icount);
		HttpURLConnection connection = (HttpURLConnection) url.openConnection();

		int status = connection.getResponseCode();
		System.out.println("Status: "+status);

		BufferedReader in = new BufferedReader(new InputStreamReader(connection.getInputStream()));
		
		String response = in.readLine(); //1st line is descriptive
		in.close();

		System.out.println("Finished writing: "+response);

		connection.disconnect();
		System.out.println("Connection closed.");
	}
}