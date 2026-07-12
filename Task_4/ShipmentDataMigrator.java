import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Map;

public class ShipmentDataMigrator {

    private static final String DB_URL = "jdbc:sqlite:shipment_database.db";

    public static void main(String[] args) {
        try (Connection conn = DriverManager.getConnection(DB_URL);
             Statement stmt = conn.createStatement()) {
            
            createTables(stmt);
            insertShippingData0(conn);
            insertShippingData2(conn);
            
            System.out.println("Data migration completed successfully.");
            
        } catch (SQLException | IOException e) {
            e.printStackTrace();
        }
    }

    private static void createTables(Statement stmt) throws SQLException {
        stmt.execute("""
            CREATE TABLE IF NOT EXISTS shipping_data_0 (
                origin_warehouse TEXT,
                destination_store TEXT,
                product TEXT,
                on_time TEXT,
                product_quantity INTEGER,
                driver_identifier TEXT
            )
        """);

        stmt.execute("""
            CREATE TABLE IF NOT EXISTS shipping_data_1 (
                shipment_identifier TEXT,
                product TEXT,
                on_time TEXT,
                origin_warehouse TEXT,
                destination_store TEXT,
                driver_identifier TEXT
            )
        """);
    }

    private static void insertShippingData0(Connection conn) throws IOException, SQLException {
        String sql = "INSERT INTO shipping_data_0 (origin_warehouse, destination_store, product, on_time, product_quantity, driver_identifier) VALUES (?, ?, ?, ?, ?, ?)";
        
        try (BufferedReader br = new BufferedReader(new FileReader("data/shipping_data_0.csv"));
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String line = br.readLine(); // Skip header row
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",");
                if (row.length >= 6) {
                    pstmt.setString(1, row[0].trim());
                    pstmt.setString(2, row[1].trim());
                    pstmt.setString(3, row[2].trim());
                    pstmt.setString(4, row[3].trim());
                    pstmt.setInt(5, Integer.parseInt(row[4].trim()));
                    pstmt.setString(6, row[5].trim());
                    pstmt.addBatch();
                }
            }
            pstmt.executeBatch(); // Execute all insertions efficiently in a batch
        }
    }

    private static void insertShippingData2(Connection conn) throws IOException, SQLException {
        // Step 1: Read shipping_data_2.csv into a Map for O(1) lookups
        Map<String, String[]> shippingData2Map = new HashMap<>();
        try (BufferedReader br = new BufferedReader(new FileReader("data/shipping_data_2.csv"))) {
            String line = br.readLine(); // Skip header row
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",");
                if (row.length >= 4) {
                    // Key: shipment_identifier -> Value: [origin_warehouse, destination_store, driver_identifier]
                    shippingData2Map.put(row[0].trim(), new String[]{row[1].trim(), row[2].trim(), row[3].trim()});
                }
            }
        }

        // Step 2: Process shipping_data_1.csv and join with the Map data
        String sql = "INSERT INTO shipping_data_1 (shipment_identifier, product, on_time, origin_warehouse, destination_store, driver_identifier) VALUES (?, ?, ?, ?, ?, ?)";
        try (BufferedReader br = new BufferedReader(new FileReader("data/shipping_data_1.csv"));
             PreparedStatement pstmt = conn.prepareStatement(sql)) {
            
            String line = br.readLine(); // Skip header row
            while ((line = br.readLine()) != null) {
                String[] row = line.split(",");
                if (row.length >= 3) {
                    String shipmentIdentifier = row[0].trim();
                    String product = row[1].trim();
                    String onTime = row[2].trim();
                    
                    if (shippingData2Map.containsKey(shipmentIdentifier)) {
                        String[] matchedRow = shippingData2Map.get(shipmentIdentifier);
                        String originWarehouse = matchedRow[0];
                        String destinationStore = matchedRow[1];
                        String driverIdentifier = matchedRow[2];
                        
                        pstmt.setString(1, shipmentIdentifier);
                        pstmt.setString(2, product);
                        pstmt.setString(3, onTime);
                        pstmt.setString(4, originWarehouse);
                        pstmt.setString(5, destinationStore);
                        pstmt.setString(6, driverIdentifier);
                        pstmt.addBatch();
                    }
                }
            }
            pstmt.executeBatch();
        }
    }
}
