package it.codhub.verifybot;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static it.codhub.verifybot.MainClass.pendingVerifications;

public class VerifyCommand extends ListenerAdapter
{

    @Override
    public void onSlashCommandInteraction(@NotNull SlashCommandInteractionEvent event)
    {

        //se il comando mandato non è verify returno
        if (!event.getName().equals("verify"))
            return;

        //ID del ruolo verificato, per controllare se l'utente è già verificato
        String ruoloMembroId = "1490450041792364765";

        //controllo se l'utente ha già il ruolo
        boolean giaVerificato = event.getMember().getRoles().stream()
                .anyMatch(role -> role.getId().equals(ruoloMembroId));

        if (giaVerificato)
        {

            //se è già verificato mando una risposta effimera
            event.reply("Sei già verificato! Non hai bisogno di farlo di nuovo.")
                    .setEphemeral(true).queue();

        }
        else
        {

            int attempts = DatabaseManager.addAttempt(event.getUser().getId());

            if (attempts > 7)
            {

                String userId = event.getUser().getId();
                String userMention = event.getUser().getAsMention();

                //applichiamo il Timeout massimo (28 giorni)
                event.getGuild().timeoutFor(event.getMember(), Duration.ofDays(28))
                        .reason("Tentativi reCAPTCHA esauriti. In attesa di revisione manuale.")
                        .queue();

                //rispondiamo all'utente con le istruzioni
                event.reply("**SISTEMA BLOCCATO**\n" +
                                "Hai fallito troppi tentativi di verifica. Sei stato messo in isolamento.\n" +
                                "**Cosa fare ora?**\n" +
                                "Se sei un umano, apri un ticket messaggiando in DM in bot ModMail per parlare con lo staff.\n" +
                                "**Attenzione:** Se non risolverai il problema con lo staff entro 24 ore, verrai bannato automaticamente.")
                        .setEphemeral(true)
                        .queue();

                //log per lo staff
                MainClass.logToDiscord("⏳ Isolamento Temporaneo",
                        "L'utente " + userMention + " è in timeout (7+ fallimenti).\nIl ban automatico scatterà tra 24 ore.",
                        java.awt.Color.ORANGE);

                //PROGRAMMIAMO IL BAN TRA 24 ORE
                MainClass.scheduler.schedule(() -> {
                    event.getGuild().retrieveMemberById(userId).queue(member -> {
                        //controlliamo se ha ancora bisogno di essere bannato
                        //(Se non ha ancora il ruolo verificato, lo banniamo)
                        boolean ancoraNonVerificato = member.getRoles().stream()
                                .noneMatch(role -> role.getId().equals(ruoloMembroId));

                        if (ancoraNonVerificato)
                        {

                            member.ban(0, TimeUnit.DAYS).reason("Ban automatico: mancata verifica dopo 24h di timeout.").queue(
                                    success -> MainClass.logToDiscord("🚫 Ban Eseguito", "L'utente " + userId + " non si è verificato entro le 24h ed è stato rimosso.", java.awt.Color.BLACK)
                            );

                        }
                    }, error -> {
                        //se l'utente è già uscito dal server, puliamo solo il DB
                        DatabaseManager.deleteUser(userId);
                    });
                }, 24, TimeUnit.HOURS);

                return;
            }

            MainClass.logToDiscord("📝 Comando Eseguito", "L'utente " + event.getUser().getAsMention() + " ha richiesto un captcha (Tentativo: " + attempts + ")", java.awt.Color.BLUE);

            //invece di usare l'ID utente direttamente uso questo:
            String token = java.util.UUID.randomUUID().toString();
            pendingVerifications.put(token, new VerificationData(event.getUser().getId()));

            //cerca l'URL nelle variabili d'ambiente, se non c'è usa localhost per i test
            String baseUrl = System.getenv("BOT_URL") != null ? System.getenv("BOT_URL") : "http://localhost:8080";
            String urlVerifica = baseUrl + "/verify?token=" + token;

            event.reply("Ciao, benvenuto! Per favore, risolvi il reCAPTCHA a questo link per ottenere l'accesso:\n" + urlVerifica)
                    .setEphemeral(true).queue();

        }

    }

}
