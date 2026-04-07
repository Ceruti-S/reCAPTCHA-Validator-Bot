# reCAPTCHA-Validator-Bot

Un bot di sicurezza professionale per Discord, progettato per proteggere i server dai raid e dagli account automatizzati tramite verifica captcha.

## Terms of Service
1. **Utilizzo del Servizio:** L'utente accetta di completare la verifica in modo onesto. Tentativi di bypass o automazione della verifica comporteranno il blocco permanente dal sistema.
2. **Limitazione di Responsabilità:** Lo sviluppatore non è responsabile per la moderazione dei singoli server. L'espulsione automatica in caso di mancata verifica è una scelta configurabile dai proprietari dei server.
3. **Disponibilità:** Il servizio è fornito "così com'è". Ci impegniamo per il massimo uptime, ma non garantiamo l'immunità totale da attacchi bot complessi.

## Privacy Policy
La protezione dei dati è la nostra priorità. Elaboriamo solo le informazioni necessarie per la verifica:

1. **Dati Elaborati:**
   - **ID Utente Discord:** Per identificare chi deve essere verificato.
   - **Stato della Verifica:** Memorizzato temporaneamente per assegnare i ruoli.
   - **Analisi del Rischio:** Durante la risoluzione del captcha, Google reCAPTCHA analizza dati tecnici (come l'indirizzo IP) per confermare che l'utente sia umano. Questi dati non vengono salvati nel database del bot.
2. **Finalità:** I dati servono esclusivamente alla sicurezza del server e all'assegnazione dei ruoli di accesso.
3. **Nessuna Condivisione:** Non vendiamo né condividiamo dati identificativi degli utenti con terze parti.
4. **Cancellazione:** I dati di sessione vengono eliminati automaticamente dopo il completamento della verifica o dopo il timeout della richiesta.
