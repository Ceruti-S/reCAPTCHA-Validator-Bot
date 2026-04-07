package it.codhub.verifybot;

import java.sql.*;

public class DatabaseManager
{

    private static Connection conn;

    public static void init()
    {

        try
        {
            //crea il file database se non esiste
            conn = DriverManager.getConnection("jdbc:sqlite:verifybot.db");
            Statement stmt = conn.createStatement();

            //tabella per monitorare i tentativi
            //userId: ID Discord
            //attempts: quanti captcha ha chiesto senza successo
            //last_request: timestamp per il reset
            String sql = """
                CREATE TABLE IF NOT EXISTS user_stats (
                    user_id TEXT PRIMARY KEY,
                    attempts INTEGER DEFAULT 0,
                    last_request INTEGER
                )
                """;
            stmt.execute(sql);
            System.out.println("[DB] Database SQLite inizializzato.");

        }
        catch (SQLException e)
        {

            e.printStackTrace();

        }

    }

    public static void close() {
        try {
            if (conn != null && !conn.isClosed()) {
                conn.close();
                System.out.println("[DB] Connessione chiusa correttamente.");
            }
        } catch (SQLException e) { e.printStackTrace(); }
    }

    //incrementa i tentativi e restituisce il numero attuale
    public static int addAttempt(String userId) {
        try {
            long now = System.currentTimeMillis();
            //24 ore in millisecondi
            long finestraTemporale = 24 * 60 * 60 * 1000;

            //recuperiamo i dati attuali dell'utente
            PreparedStatement select = conn.prepareStatement("SELECT attempts, last_request FROM user_stats WHERE user_id = ?");
            select.setString(1, userId);
            ResultSet rs = select.executeQuery();

            if (rs.next()) {
                long lastRequest = rs.getLong("last_request");
                int currentAttempts = rs.getInt("attempts");

                if (now - lastRequest > finestraTemporale) {
                    //è passato più di un giorno: RESETTIAMO il conteggio
                    PreparedStatement reset = conn.prepareStatement(
                            "UPDATE user_stats SET attempts = 1, last_request = ? WHERE user_id = ?");
                    reset.setLong(1, now);
                    reset.setString(2, userId);
                    reset.executeUpdate();
                    return 1;
                } else {
                    //siamo dentro le 24 ore: INCREMENTIAMO
                    PreparedStatement update = conn.prepareStatement(
                            "UPDATE user_stats SET attempts = attempts + 1, last_request = ? WHERE user_id = ?");
                    update.setLong(1, now);
                    update.setString(2, userId);
                    update.executeUpdate();
                    return currentAttempts + 1;
                }
            } else {
                //primo tentativo assoluto
                PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO user_stats (user_id, attempts, last_request) VALUES (?, 1, ?)");
                insert.setString(1, userId);
                insert.setLong(2, now);
                insert.executeUpdate();
                return 1;
            }
        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    public static int getAttempts(String userId)
    {

        try
        {

            PreparedStatement pstmt = conn.prepareStatement("SELECT attempts FROM user_stats WHERE user_id = ?");
            pstmt.setString(1, userId);
            ResultSet rs = pstmt.executeQuery();
            if (rs.next())
                return rs.getInt("attempts");

        }
        catch (SQLException e)
        {

            e.printStackTrace();

        }

        return 0;

    }

    public static void deleteUser(String userId)
    {

        try
        {

            PreparedStatement pstmt = conn.prepareStatement("DELETE FROM user_stats WHERE user_id = ?");
            pstmt.setString(1, userId);
            pstmt.executeUpdate();

        }
        catch (SQLException e)
        {

            e.printStackTrace();

        }

    }

}
