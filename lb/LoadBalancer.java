package LB;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.OutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;

import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.net.URL;
import java.net.URLEncoder;
import java.net.HttpURLConnection;

import com.amazonaws.services.ec2.AmazonEC2;
import com.amazonaws.services.ec2.AmazonEC2ClientBuilder;

import com.amazonaws.services.ec2.model.RunInstancesRequest;
import com.amazonaws.services.ec2.model.RunInstancesResult;
import com.amazonaws.services.ec2.model.InstanceType;

import com.amazonaws.services.ec2.model.StartInstancesRequest;

import com.amazonaws.services.ec2.model.DescribeInstancesRequest;
import com.amazonaws.services.ec2.model.DescribeInstancesResult;
import com.amazonaws.services.ec2.model.Instance;
import com.amazonaws.services.ec2.model.Reservation;

import com.amazonaws.services.cloudwatch.AmazonCloudWatch;
import com.amazonaws.services.cloudwatch.AmazonCloudWatchClientBuilder;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsRequest;
import com.amazonaws.services.cloudwatch.model.DescribeAlarmsResult;
import com.amazonaws.services.cloudwatch.model.MetricAlarm;

public class LoadBalancer {

	//Maps icount to instance id
	private static Map<String, Integer> ICountMap = new HashMap<String, Integer>();

    public static void main(final String[] args) throws IOException {
		final String serverAddress = "0.0.0.0"; //localhost for now
        final int serverPort = 8000;

        final HttpServer server = HttpServer.create(new InetSocketAddress(serverAddress, serverPort), 0);

        server.createContext("/receiveResult", new ReceiveResultHandler());
		server.createContext("/scan", new LBToWebserverHandler());
		server.setExecutor(Executors.newCachedThreadPool());
		server.start();
    }

	private static class LBToWebserverHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange exchange) throws IOException {
			//The LB gets the request here
			final String query = exchange.getRequestURI().getQuery();
			System.out.println("> Query:\t" + query);

			//Then sees the status of each instance

			//Then chooses the best instance to send

			//Then sends the request it got to the chosen instance
			String result = "Hello :D";
			byte[] response = result.getBytes();
        	exchange.sendResponseHeaders(200, response.length);
        	OutputStream os = exchange.getResponseBody();
        	os.write(response);
        	os.close();
			
            System.out.println("> Sent response to " + exchange.getRemoteAddress().toString());
		}
	}

	private static void sendRequestToWebServer(String instanceIP) throws IOException{
		String serverAddress = "0";
		String serverPort = "0";
		String scan_type = "0";
		String area = "0";

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
	}

	private static class ReceiveResultHandler implements HttpHandler {

		@Override
		public void handle(final HttpExchange exchange) throws IOException {
			System.out.println("Hewo.");
		}
	}
	
	public void example(){
		final AmazonEC2 ec2 = AmazonEC2ClientBuilder.defaultClient();

		// CREATING INSTANCE, isto talvez só exista no inicio do nosso sistema
		// depois damos start e stop às instancias conforme necessário 
		// TALVEZ, isto pq n vejo como dar terminate a instances pelo SDK

        RunInstancesRequest run_request = new RunInstancesRequest()
			.withImageId("ami-0d5eff06f840b45e9")
			.withInstanceType(InstanceType.T2Micro)
			.withMinCount(1)
			.withMaxCount(1)
			.withKeyName("CNV-MyKeyPair")
			.withSecurityGroups("CNV-SSH-HTTP");
		
		/*
        RunInstancesResult run_response = ec2.runInstances(run_request);
        String reservation_id = run_response.getReservation().getInstances().get(0).getInstanceId();
		System.out.println("Success: "+reservation_id);
		*/
	//lol
		// STARTING INSTANCE
		/*
		StartInstancesRequest start_request = new StartInstancesRequest()
    		.withInstanceIds("i-0afe583789a8a2ec2");

		ec2.startInstances(start_request);
		*/

		// STOPPING INSTANCE
		/*
		StopInstancesRequest request = new StopInstancesRequest()
    		.withInstanceIds("i-0afe583789a8a2ec2");

		ec2.stopInstances(request);
		*/

		// DESCRIBING INSTANCE, conseguimos o IP por aqui
		boolean done = false;
		DescribeInstancesRequest describe_request = new DescribeInstancesRequest();
		while(!done) {
    		DescribeInstancesResult response = ec2.describeInstances(describe_request);

    		for(Reservation reservation : response.getReservations()) {
        		for(Instance instance : reservation.getInstances()) {
            		System.out.printf(
                		"Found instance with id %s, " +
                		"AMI %s, " +
                		"type %s, " +
						"ip %s, " +
                		"state %s " +
                		"and monitoring state %s\n",
                		instance.getInstanceId(),
                		instance.getImageId(),
                		instance.getInstanceType(),
						instance.getPublicIpAddress(),
                		instance.getState().getName(),
                		instance.getMonitoring().getState());
        		}
    		}

    		describe_request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}

		//DESCRIBING ALARM, status gives OK or IN ALARM, usamos isso
		final AmazonCloudWatch cw = AmazonCloudWatchClientBuilder.defaultClient();

		done = false;
		DescribeAlarmsRequest request = new DescribeAlarmsRequest();

		while(!done) {
    		DescribeAlarmsResult response = cw.describeAlarms(request);
			for(MetricAlarm alarm : response.getMetricAlarms()) {
				System.out.printf(
						"Retrieved alarm %s, " +
                		"Status %s\n",
                		alarm.getAlarmName(),
                		alarm.getStateValue());
    		}

    		request.setNextToken(response.getNextToken());

    		if(response.getNextToken() == null) {
        		done = true;
    		}
		}
	}
}