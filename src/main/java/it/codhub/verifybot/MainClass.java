package it.codhub.verifybot;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import io.javalin.Javalin;
import org.json.JSONObject;

import java.awt.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

public class MainClass {

    public static JDA jda;

    //mappa che associa il Token UUID all'oggetto VerificationData (ID utente + timestamp)
    protected static final Map<String, VerificationData> pendingVerifications = new ConcurrentHashMap<>();

    protected static final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(1);

    public static void main(String[] args) throws InterruptedException
    {

        DatabaseManager.init();

        // --- PARTE DISCORD ---
        String discordToken = System.getenv("DISCORD_TOKEN");
        jda = JDABuilder.createDefault(discordToken)
                .addEventListeners(new VerifyCommand())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .build();

        jda.awaitReady();
        jda.updateCommands().addCommands(
                net.dv8tion.jda.api.interactions.commands.build.Commands.slash("verify", "Avvia la procedura di verifica tramite reCAPTCHA di Google")
        ).queue();

        //creo un thread separato che controlla ogni 15 minuti se ci sono token scaduti
        scheduler.scheduleAtFixedRate(() -> {
            long scadenza = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(15);
            int rimossi = pendingVerifications.size();

            //rimuove tutti i token creati più di 15 minuti fa
            pendingVerifications.entrySet().removeIf(entry -> entry.getValue().timestamp < scadenza);

            rimossi -= pendingVerifications.size();
            if (rimossi > 0)
            {

                System.out.println("[Sistema] Pulizia completata: rimossi " + rimossi + " token scaduti.");
                logToDiscord("Token scaduti rimossi", "Lo spazzino ha rimosso " + rimossi + " token scaduti.", java.awt.Color.CYAN);

            }

        }, 15, 15, TimeUnit.MINUTES);

        // --- PARTE WEB ---
        var app = Javalin.create().start(8080);

        //shutdown Hook per spegnere tutto correttamente
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("Segnale di chiusura ricevuto! Spegnimento in corso...");
            if (jda != null) jda.shutdown();
            if (app != null) app.stop();
            scheduler.shutdown(); //spegne lo spazzino
            DatabaseManager.close();
            System.out.println("Bot e servizi spenti correttamente.");
        }));

        //endpoint GET: Carica la pagina di verifica
        app.get("/verify", ctx -> {
            String token = ctx.queryParam("token");
            String siteKey = System.getenv("RECAPTCHA_SITE_KEY");

            //verifica che il token esista e non sia scaduto (lo spazzino lo avrebbe già rimosso)
            if (token == null || !pendingVerifications.containsKey(token))
            {

                ctx.html("<h1 style='color:red; text-align:center; font-family:sans-serif;'>Link non valido o scaduto.<br>Torna su Discord e usa di nuovo /verify.</h1>");
                return;

            }

            String html = """
            <!DOCTYPE html>
            <html lang="it">
            <head>
                <meta charset="UTF-8">
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <title>CodHub IT | Verifica anti-bot</title>
                <script src="https://www.google.com/recaptcha/api.js" async defer></script>
                <style>
                    * { box-sizing: border-box; }
                    body { font-family: 'Segoe UI', sans-serif; background-color: #2c2f33; color: white; display: flex; justify-content: center; align-items: center; height: 100vh; margin: 0; }
                    .card { background-color: #23272a; padding: 40px; border-radius: 12px; box-shadow: 0 8px 24px rgba(0,0,0,0.5); text-align: center; max-width: 420px; width: 90%%; border: 1px solid #7289da; }
                    h1 { color: #7289da; margin: 0 0 10px 0; }
                    p { color: #b9bbbe; margin-bottom: 30px; }
                    .g-recaptcha { display: flex; justify-content: center; margin-bottom: 25px; }
                    .btn-submit { background-color: #5865f2; color: white; border: none; padding: 14px; border-radius: 5px; cursor: pointer; font-weight: bold; width: 100%%; transition: 0.2s; }
                    .btn-submit:hover { background-color: #4752c4; }
                </style>
            </head>
            <body>
                <div class="card">
                    <h1>CodHub IT</h1>
                    <p>Risolvi il captcha qui sotto per sbloccare i canali del server.</p>
                    <form action="/validate" method="POST" onsubmit="return validateForm()">
                        <input type="hidden" name="token" value="%s">
                        <div class="g-recaptcha" data-sitekey="%s" data-theme="dark"></div>
                        <button type="submit" class="btn-submit">Verificami</button>
                    </form>
                </div>
                <script>
                    function validateForm() {
                        if(grecaptcha.getResponse().length == 0) {
                            alert('Spunta la casella del captcha!');
                            return false;
                        }
                        return true;
                    }
                </script>
            </body>
            </html>
            """.formatted(token, siteKey);

            ctx.html(html);
        });

        //endpoint POST: Valida il reCAPTCHA e assegna il ruolo
        app.post("/validate", ctx -> {
            String token = ctx.formParam("token");
            String gResponse = ctx.formParam("g-recaptcha-response");

            VerificationData data = (token != null) ? pendingVerifications.get(token) : null;

            if (data == null)
            {

                ctx.html("<h1>Sessione scaduta o link non valido.</h1>");
                return;

            }

            if (gResponse == null || gResponse.isEmpty())
            {

                ctx.html("<h1>Captcha mancante. Torna indietro e riprova.</h1>");
                return;

            }

            String secretKey = System.getenv("RECAPTCHA_SECRET");
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(7)).build();
            String params = "secret=" + secretKey + "&response=" + gResponse;

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create("https://www.google.com/recaptcha/api/siteverify"))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .timeout(Duration.ofSeconds(10))
                    .POST(HttpRequest.BodyPublishers.ofString(params))
                    .build();

            try
            {

                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                JSONObject jsonResponse = new JSONObject(response.body());

                if (jsonResponse.getBoolean("success"))
                {

                    daRuolo(data.userId); //usa l'ID salvato nei dati del token
                    pendingVerifications.remove(token); //il token è stato usato, lo eliminiamo subito
                    DatabaseManager.deleteUser(data.userId);

                    logToDiscord("Verifica Superata", "L'utente <@" + data.userId + "> ha completato il reCAPTCHA con successo.", java.awt.Color.GREEN);

                    ctx.html("<!DOCTYPE html><html lang='it'><body style='background-color:#2c2f33;color:white;text-align:center;font-family:sans-serif;padding-top:100px;'>" +
                            "<h1>Verifica Completata!</h1><p>Puoi tornare su Discord.</p></body></html>");

                }
                else
                {

                    logToDiscord("Verifica Fallita", "L'utente <@" + data.userId + "> ha fallito la verifica reCAPTCHA.", java.awt.Color.ORANGE);

                    ctx.html("<h1>Captcha fallito. Riprova.</h1>");

                }

            }
            catch (Exception e)
            {

                e.printStackTrace();
                ctx.html("<h1>Errore di comunicazione con Google.</h1>");
                logToDiscord("Errore sistema", "C'è stato un errore di comunicazione con Google", java.awt.Color.GRAY);

            }

        });

    }

    public static void daRuolo(String userId)
    {

        try
        {

            var guild = jda.getGuildById("1490437168466755865");
            var role = guild.getRoleById("1490450041792364765");

            if (guild != null && role != null)
            {

                guild.retrieveMemberById(userId).queue(member -> {
                    guild.addRoleToMember(member, role).queue(
                            success -> System.out.println("Ruolo assegnato a " + member.getEffectiveName()),
                            error -> System.err.println("Errore assegnazione ruolo: " + error.getMessage())
                    );

                },
                        error -> System.err.println("Utente non trovato nel server."));
            }

        }
        catch (Exception e)
        {

            e.printStackTrace();

        }

    }

    public static void logToDiscord(String title, String description, java.awt.Color color)
    {

        var guild = jda.getGuildById("1490437168466755865");
        var channel = guild.getTextChannelById("1490801635973136545");

        if (channel != null)
        {

            net.dv8tion.jda.api.EmbedBuilder embed = new net.dv8tion.jda.api.EmbedBuilder();
            embed.setTitle(title);
            embed.setDescription(description);
            embed.setColor(color);
            embed.setTimestamp(java.time.Instant.now());
            channel.sendMessageEmbeds(embed.build()).queue();

        }

    }

}

//classe di supporto per associare ID Utente e ora di creazione
class VerificationData
{

    String userId;
    long timestamp;

    VerificationData(String userId)
    {

        this.userId = userId;
        this.timestamp = System.currentTimeMillis();

    }

}