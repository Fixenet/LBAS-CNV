package mss;

import java.io.IOException;
import java.io.OutputStream;

import java.net.InetSocketAddress;

import java.util.ArrayList;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import com.amazonaws.AmazonServiceException;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDB;
import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClientBuilder;

import com.amazonaws.services.dynamodbv2.model.AttributeDefinition;
import com.amazonaws.services.dynamodbv2.model.TableDescription;
import com.amazonaws.services.dynamodbv2.model.CreateTableRequest;
import com.amazonaws.services.dynamodbv2.model.CreateTableResult;
import com.amazonaws.services.dynamodbv2.model.KeySchemaElement;
import com.amazonaws.services.dynamodbv2.model.KeyType;
import com.amazonaws.services.dynamodbv2.model.ProvisionedThroughput;
import com.amazonaws.services.dynamodbv2.model.ScalarAttributeType;

import com.amazonaws.services.dynamodbv2.document.DynamoDB;
import com.amazonaws.services.dynamodbv2.document.RangeKeyCondition;
import com.amazonaws.services.dynamodbv2.document.ItemCollection;
import com.amazonaws.services.dynamodbv2.document.Item;
import com.amazonaws.services.dynamodbv2.document.PutItemOutcome;
import com.amazonaws.services.dynamodbv2.document.Table;

public class MetricStorageSystem {
	private static final AmazonDynamoDB client = AmazonDynamoDBClientBuilder.defaultClient();
	private static final DynamoDB dynamoDB = new DynamoDB(client);
	private static Table table;

	private static String TABLE_NAME = "Metrics";
	// primary keys
	private static String HASH_KEY = "ScanType";
	private static String RANGE_KEY = "RequestArea";
	// value
	private static String VALUE = "ICount";

	private static final ProvisionedThroughput THRUPUT = new ProvisionedThroughput(1L, 2L);

	//MetricStorageSystem
	private static final String MSSserverAddress = "127.0.0.1"; //localhost because they are running on the same machine
    private static final int MSSserverPort = 8001;

    public static void main(final String[] args) throws IOException {
        final HttpServer server = HttpServer.create(new InetSocketAddress(MSSserverAddress, MSSserverPort), 0);

        server.createContext("/storeMetric", new StoreMetricHandler());
		server.createContext("/getEstimateMetric", new GetEstimateHandler());
        server.setExecutor(Executors.newCachedThreadPool());
		server.start();

		try {
			createTable();
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
    }

	private static CreateTableRequest newCreateTableRequest(String tableName) {
        CreateTableRequest req = new CreateTableRequest()
            .withTableName(tableName)
            .withAttributeDefinitions(
                new AttributeDefinition(HASH_KEY, ScalarAttributeType.S),
                new AttributeDefinition(RANGE_KEY, ScalarAttributeType.N))
            .withKeySchema(
                new KeySchemaElement(HASH_KEY, KeyType.HASH),
                new KeySchemaElement(RANGE_KEY, KeyType.RANGE))
            .withProvisionedThroughput(THRUPUT);
        return req;
    }

	private static void createTable() throws InterruptedException {
		System.out.println("Checking if table already exists...");
        table = dynamoDB.getTable(TABLE_NAME);
        // Check if table already exists, and if so wait for it to become active
        TableDescription desc = table.waitForActiveOrDelete();
        if (desc != null) {
            System.out.println("Skip creating table which already exists and ready for use: " + desc);
            return;
        }
        // Table doesn't exist.  Let's create it.
        table = dynamoDB.createTable(newCreateTableRequest(TABLE_NAME));
        // Wait for the table to become active 
        desc = table.waitForActive();
        System.out.println("Table is ready for use! " + desc);
	}

	private static void putItemInTable(String scan_type, int scan_area, int block_count) throws InterruptedException {
		table.waitForActive();
		table.putItem(new Item().withString(HASH_KEY, scan_type)
			.withInt(RANGE_KEY, scan_area)
			.withInt(VALUE, block_count));
	}

	private static int getEstimateItemFromTable(String scan_type, int scan_area) throws InterruptedException {
		table.waitForActive();
        ItemCollection<?> col = table.query(HASH_KEY, scan_type, 
            new RangeKeyCondition(RANGE_KEY).ge(scan_area)); 
        for (Item item: col) return item.getInt(VALUE);
		return -1;
	}

    static class StoreMetricHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {

			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			final ArrayList<String> args = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				args.add(splitParam[1]);
			}

			try {
				putItemInTable(args.get(0), Integer.parseInt(args.get(1)), Integer.parseInt(args.get(2)));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
			String estimate = "MSS Stored Metric: Scan="+args.get(0)+",Area="+args.get(1)+",BlockCount="+args.get(2)+"\n";
			System.out.println(estimate);
			byte[] response = estimate.getBytes();
        	t.sendResponseHeaders(200, response.length);
        	OutputStream os = t.getResponseBody();
        	os.write(response);
        	os.close();
			
            System.out.println("> Sent response to " + t.getRemoteAddress().toString());
		}
	}

	static class GetEstimateHandler implements HttpHandler {
		@Override
		public void handle(final HttpExchange t) throws IOException {

			// Get the query.
			final String query = t.getRequestURI().getQuery();

			System.out.println("> Query:\t" + query);

			// Break it down into String[].
			final String[] params = query.split("&");

			final ArrayList<String> args = new ArrayList<>();
			for (final String p : params) {
				final String[] splitParam = p.split("=");
				args.add(splitParam[1]);
			}

			int result = -1;
			try {
				result = getEstimateItemFromTable(args.get(0), Integer.parseInt(args.get(1)));
			} catch (InterruptedException e) {
				e.printStackTrace();
			}

			// Send response to browser.
			final Headers hdrs = t.getResponseHeaders();

			hdrs.add("Access-Control-Allow-Origin", "*");
			hdrs.add("Access-Control-Allow-Credentials", "true");
			hdrs.add("Access-Control-Allow-Methods", "POST, GET, HEAD, OPTIONS");
			hdrs.add("Access-Control-Allow-Headers", "Origin, Accept, X-Requested-With, Content-Type, Access-Control-Request-Method, Access-Control-Request-Headers");
			
			String bcount = "MSS Estimate: Scan="+args.get(0)+",Area="+args.get(1)+" => "+result+"\n"+result;
			System.out.println(bcount);
			byte[] response = bcount.getBytes();
        	t.sendResponseHeaders(200, response.length);
        	OutputStream os = t.getResponseBody();
        	os.write(response);
        	os.close();
			
            System.out.println("> Sent response to " + t.getRemoteAddress().toString());
		}
	}
}