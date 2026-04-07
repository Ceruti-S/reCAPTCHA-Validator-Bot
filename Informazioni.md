# ℹ️ Informazioni Tecniche - reCAPTCHA Validator

Questo documento descrive l'architettura tecnica del bot di verifica di **CodHub IT**. Il codice sorgente completo è disponibile nel branch `master`.

## Stack Tecnologico
* **Java 21 & JDA:** Core del bot per l'interazione con l'API di Discord.
* **Javalin:** Micro-web framework utilizzato per servire la pagina di verifica e gestire i callback di Google.
* **SQLite:** Database locale utilizzato per il tracking dei tentativi di verifica e la prevenzione del brute-forcing.
* **Google reCAPTCHA v2:** Sistema esterno utilizzato per la validazione dell'identità umana.

---

## Logica di Funzionamento

### 1. Sistema di Token Univoci
A differenza di sistemi statici, questo bot genera un **UUID (Universally Unique Identifier)** per ogni richiesta di verifica (`/verify`). 
- Il token viene salvato in una `ConcurrentHashMap` (`pendingVerifications`) insieme all'ID utente e a un timestamp.
- Un sistema di "spazzino" (`ScheduledExecutorService`) pulisce automaticamente la memoria ogni 15 minuti, rimuovendo i token scaduti per prevenire attacchi di tipo DoS alla memoria del bot.

### 2. Sicurezza e Anti-Spam (Database)
Il bot monitora i tentativi falliti tramite `DatabaseManager.java`:
- **Limite Tentativi:** Se un utente fallisce la verifica per più di 7 volte in 24h, scatta il blocco automatico.
- **Isolamento (Timeout):** L'utente viene messo in "Timeout" su Discord per 28 giorni.
- **Auto-Ban:** Se l'utente non risolve la situazione con lo staff (es. via ModMail) entro 24 ore dal blocco, il bot esegue un ban automatico programmato.

### 3. Workflow della Verifica
1. L'utente lancia il comando `/verify`.
2. Il bot genera un link dinamico: `https://dominio.com/verify?token=UUID`. (in fase di sviluppo e integrazione, percui ancora non funzionante)
3. Javalin serve una pagina HTML protetta da reCAPTCHA.
4. Al click su "Verificami", il server effettua una chiamata POST `siteverify` ai server di Google.
5. In caso di successo, il bot assegna il ruolo tramite `guild.addRoleToMember` e pulisce i dati temporanei.

---

## Struttura Branch
Il progetto segue una struttura Maven standard nel branch **master**. I segreti (Token Discord, Secret Key di Google) sono gestiti esclusivamente tramite variabili d'ambiente per garantire la massima sicurezza del codice.

**CodHub IT | Bot Development Team**
