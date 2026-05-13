# ASL5

# TOPSENTINEL MAIL GALLERY

## Documentazione tecnica

Sistema di monitoraggio endpoint Gallery e invio alert mail

Versione: 1.1

Data: 12/05/2026

Progetto: TopSentinelMailGallery

| Autore | Revisione | Versione | Data | Modifica |
| --- | --- | --- | --- | --- |
| m.manzotti | Prima emissione | 1.0 | 08/05/2026 | Stesura documentazione tecnica del progetto TopSentinelMailGallery |
| m.manzotti | Revisione tecnica | 1.1 | 12/05/2026 | Allineamento alla struttura documentale di riferimento e integrazione sezioni operative, sicurezza, deployment e troubleshooting |

## Indice

| Sezione | Titolo |
| --- | --- |
| 1 | Introduzione |
| 2 | Scopo del documento |
| 3 | Ambito di applicazione |
| 4 | Descrizione generale del sistema |
| 4.1 | Architettura logica |
| 4.2 | Modalita di funzionamento |
| 4.3 | Integrazione con l'infrastruttura |
| 4.4 | Caratteristiche principali |
| 4.5 | Schema logico del sistema |
| 5 | Architettura del sistema |
| 5.1 | Architettura logica |
| 5.2 | Componenti del sistema |
| 5.3 | Dipendenze e librerie |
| 5.4 | Flussi di comunicazione |
| 5.5 | Gestione dei dati |
| 5.6 | Sicurezza e comunicazioni |
| 6 | Flusso operativo |
| 6.1 | Inizializzazione del processo |
| 6.2 | Recupero e preparazione dati |
| 6.3 | Verifica raggiungibilita |
| 6.4 | Aggiornamento stato endpoint |
| 6.5 | Politica alert mail |
| 6.6 | Logging e riepilogo |
| 7 | Configurazione |
| 8 | Gestione errori e logging |
| 9 | Sicurezza e manutenibilita |
| 10 | Requisiti di sistema |
| 11 | Deployment |
| 12 | Ambiente di esercizio |
| 13 | Troubleshooting |
| 14 | Allegati |

## 1. Introduzione

Il presente documento descrive l'architettura, le componenti e il funzionamento del sistema TopSentinelMailGallery. Il sistema e' progettato per controllare periodicamente la raggiungibilita degli endpoint Gallery censiti a database, aggiornare lo stato tecnico degli endpoint e notificare via mail eventuali condizioni di indisponibilita.

L'applicazione opera in modalita batch Java standalone ed e' pensata per essere eseguita da schedulazione automatica su ambiente server Windows, senza interazione diretta da parte dell'utente finale.

Il documento riprende l'impostazione della documentazione tecnica di riferimento del progetto AlboPretorioNew: frontespizio, registro revisioni, indice, descrizione architetturale, flusso operativo, configurazione, requisiti, deployment, ambiente di esercizio e troubleshooting.

## 2. Scopo del documento

Lo scopo del documento e' fornire una descrizione tecnica utile per gestione, manutenzione, deployment ed evoluzione del software. Le informazioni sono rivolte al personale tecnico incaricato di presidiare il job, il database applicativo, il servizio mail e l'ambiente di esercizio.

Il documento non sostituisce il codice sorgente, ma ne sintetizza comportamento, configurazione, prerequisiti, flussi di comunicazione e punti di attenzione operativa.

## 3. Ambito di applicazione

TopSentinelMailGallery si applica al monitoraggio tecnico degli endpoint Gallery registrati nella tabella TOPSENTINEL_ENDPOINT. Il controllo avviene tramite apertura socket verso host e porta configurati; l'esito viene salvato a database e, nei casi previsti, viene inviata una comunicazione email tramite servizio SOAP TopMail.

Il sistema copre le seguenti attivita:

- lettura degli endpoint da database Oracle;
- verifica della raggiungibilita host/porta con timeout configurabile;
- aggiornamento dei campi di stato e data ultimo controllo;
- registrazione della data di ultima raggiungibilita;
- invio di alert mail in caso di cambio stato o promemoria periodico;
- produzione di log testuale su console, file batch e cartella dedicata agli invii mail.

Sono esclusi dall'ambito del sistema la gestione funzionale della piattaforma Gallery, la modifica dei contenuti applicativi pubblicati dalla Gallery e il presidio infrastrutturale dei server remoti.

## 4. Descrizione generale del sistema

TopSentinelMailGallery e' un'applicazione batch Java che viene avviata, esegue il ciclo di controllo sugli endpoint configurati e termina. Non espone interfacce web, API HTTP o servizi in ascolto.

### 4.1 Architettura logica

Il sistema e' composto dai seguenti moduli principali:

- modulo di accesso ai dati, implementato dalla classe DataService;
- modulo di controllo endpoint, implementato nella classe TopSentinelMailGallery;
- modulo di invio email, implementato dalla classe TopMailService;
- modulo di logging operativo, basato su standard output, standard error e file system.

Il database Oracle rappresenta la sorgente dati degli endpoint e il punto di persistenza dello stato tecnico corrente. Il servizio TopMail e' utilizzato per l'invio delle notifiche di indisponibilita. Il file system ospita i log prodotti dal batch e il log giornaliero degli invii mail.

### 4.2 Modalita di funzionamento

A ogni esecuzione il job apre una connessione al database, legge tutte le righe presenti in TOPSENTINEL_ENDPOINT, normalizza l'host indicato nel campo ENDPOINT, verifica la connessione TCP verso la PORTA configurata e aggiorna lo stato ATTIVO.

La data di riferimento per le decisioni operative viene letta dal database tramite SYSDATE. Questa scelta consente di basare la finestra di promemoria mail sull'orario del sistema informativo centrale e non sull'orologio locale della macchina che esegue il batch.

### 4.3 Integrazione con l'infrastruttura

| Integrazione | Descrizione |
| --- | --- |
| Database Oracle | Connessione JDBC diretta tramite driver Oracle. Lettura e aggiornamento della tabella TOPSENTINEL_ENDPOINT. |
| Endpoint Gallery | Verifica tecnica mediante socket TCP su host e porta configurati. |
| Servizio TopMail | Invio alert tramite richiesta SOAP HTTP POST verso TopMail.asmx, metodo InvioMailConCCN. |
| File system | Scrittura del log batch nella cartella locale di esecuzione e del log mail nella cartella configurata. |
| Windows Task Scheduler | Esecuzione ricorrente del batch run-topsentinel-mail-gallery.cmd. |

### 4.4 Caratteristiche principali

Il sistema presenta le seguenti caratteristiche:

- applicazione Java 8 standalone con main class TopSentinelMailGallery.TopSentinelMailGallery;
- avvio da riga di comando o tramite file batch;
- parametri runtime sovrascrivibili da CLI;
- timeout connessione endpoint configurabile;
- normalizzazione host da URL, host:porta o indirizzo semplice;
- aggiornamento parametrico dello stato endpoint tramite PreparedStatement;
- politica mail progettata per evitare invii continui fuori dalla finestra di promemoria;
- log di riepilogo con conteggi di endpoint raggiungibili, non raggiungibili, record aggiornati e mail inviate/non inviate.

### 4.5 Schema logico del sistema

#### 4.5.1 Schema logico

```text
Windows Task Scheduler
        |
        v
run-topsentinel-mail-gallery.cmd
        |
        v
TopSentinelMailGallery.jar
        |
        +-- Oracle DB: TOPSENTINEL_ENDPOINT
        |
        +-- Socket TCP verso endpoint Gallery
        |
        +-- SOAP HTTP: TopMail.asmx
        |
        +-- File system: log applicativi e log invio mail
```

#### 4.5.2 Descrizione del flusso

Il flusso operativo e' sequenziale:

1. il job viene avviato dallo scheduler o manualmente;
2. vengono letti e validati gli argomenti CLI;
3. viene aperta la connessione al database Oracle;
4. viene letto SYSDATE dal database;
5. vengono recuperati gli endpoint censiti in TOPSENTINEL_ENDPOINT;
6. ogni endpoint viene verificato tramite socket TCP;
7. lo stato viene aggiornato sul database;
8. se previsto dalla politica di alert, viene inviata una mail;
9. vengono prodotti log di dettaglio e riepilogo;
10. il processo termina.

#### 4.5.3 Caratteristiche del modello

Il modello e' volutamente lineare e separa il livello dati dalla logica applicativa e dall'integrazione email. La mancata raggiungibilita di un endpoint e' trattata come esito applicativo atteso e gestita per singola riga, mentre gli errori SQL sono considerati bloccanti per l'esecuzione.

## 5. Architettura del sistema

### 5.1 Architettura logica

L'architettura puo' essere suddivisa nei seguenti livelli:

- livello dati, rappresentato dal database Oracle;
- livello applicativo, rappresentato dal JAR TopSentinelMailGallery;
- livello di integrazione, costituito dal servizio SOAP TopMail e dalle verifiche socket verso gli endpoint;
- livello operativo, costituito da scheduler, file batch e log.

### 5.2 Componenti del sistema

| Componente | File | Responsabilita |
| --- | --- | --- |
| Job principale | src/TopSentinelMailGallery/TopSentinelMailGallery.java | Parsing parametri, ciclo di controllo endpoint, aggiornamento database, logica alert e riepilogo esecuzione. |
| Servizio mail | src/TopSentinelMailGallery/TopMailService.java | Costruzione envelope SOAP, invio HTTP POST, gestione response code e SOAP Fault. |
| Accesso dati | src/TopSentinelMailGallery/DataService.java | Apertura connessione tramite DataSource JNDI se configurato o DriverManager JDBC. |
| Batch avvio | dist/run-topsentinel-mail-gallery.cmd | Imposta directory di lavoro, seleziona Java, crea cartella logs e reindirizza stdout/stderr. |
| Pacchetto server | TopSentinelMailGallery-server.zip | Archivio di distribuzione con JAR, librerie e batch di avvio. |

### 5.3 Dipendenze e librerie

| Libreria | Uso previsto |
| --- | --- |
| ojdbc8.jar | Driver JDBC Oracle usato dal runtime di default. |
| ojdbc6.jar | Driver Oracle presente nel progetto per compatibilita, non usato dal classpath NetBeans corrente. |
| commons-codec-1.10.jar | Dipendenza inclusa nel classpath NetBeans. |
| gestioneDB.jar | Libreria applicativa aziendale disponibile nel progetto. |
| tp-base.jar | Libreria applicativa aziendale disponibile nel progetto. |
| mssql-jdbc-9.2.0.jre8.jar / mssql-jdbc-9.4.0.jre8.jar | Driver SQL Server presenti nel progetto ma non usati dal flusso Oracle di default. |

Il progetto e' configurato come applicazione Java standard NetBeans, con build Ant e target Java 1.8.

### 5.4 Flussi di comunicazione

I flussi principali sono:

1. accesso JDBC al database Oracle;
2. apertura socket TCP verso ciascun endpoint Gallery;
3. invio HTTP SOAP verso il servizio TopMail;
4. scrittura su file system locale o share di rete.

L'applicazione non mantiene connessioni persistenti oltre la durata dell'esecuzione. Le risorse JDBC e socket vengono chiuse al termine del relativo blocco di utilizzo.

### 5.5 Gestione dei dati

La tabella TOPSENTINEL_ENDPOINT contiene gli endpoint da verificare e lo stato tecnico associato. Il job legge le colonne ID, ENDPOINT, PORTA e ATTIVO. Dopo il controllo aggiorna ATTIVO, LAST_UPDATE e, in caso di esito positivo, ULTIMA_VOLTA_RAGGIUNGIBILE.

Il database e' anche la sorgente per la data di riferimento del processo. La query SELECT SYSDATE FROM DUAL viene eseguita all'inizio del ciclo per determinare la finestra dei promemoria mail.

### 5.6 Sicurezza e comunicazioni

Le comunicazioni principali avvengono su rete interna. La connessione al database usa JDBC Oracle, la verifica endpoint usa socket TCP e l'invio email usa HTTP SOAP verso TopMail.

Le credenziali database devono essere trattate come segreto operativo. La password di default presente nel codice non viene riportata in questo documento e deve essere preferibilmente sostituita da configurazione esterna o da argomenti gestiti nello scheduler.

## 6. Flusso operativo

### 6.1 Inizializzazione del processo

All'avvio il metodo main verifica la presenza dei flag --help o -h. In caso positivo stampa le opzioni supportate e termina. In assenza di help, gli argomenti vengono letti nel formato --chiave=valore e validati dalla classe interna Config.

Parametri senza prefisso --, senza separatore = o con chiave non supportata sono considerati non validi; in questo caso l'applicazione stampa l'errore, mostra l'usage ed esce con codice 2.

### 6.2 Recupero e preparazione dati

Il job apre una connessione database tramite DataService. Nel flusso attuale il nome JNDI e' null, quindi viene caricata la classe driver JDBC e viene usato DriverManager.

Le query principali sono:

```sql
SELECT SYSDATE FROM DUAL
```

```sql
SELECT * FROM TOPSENTINEL_ENDPOINT
```

Per ogni riga viene costruita una struttura interna EndpointRow contenente ID, ENDPOINT, PORTA e ATTIVO precedente.

### 6.3 Verifica raggiungibilita

Prima del controllo l'endpoint viene normalizzato:

- se contiene uno schema URL viene estratto l'host;
- eventuali path vengono rimossi;
- nel caso host:porta viene mantenuta solo la parte host;
- gli indirizzi IPv6 racchiusi tra parentesi quadre vengono ripuliti;
- host vuoto o porta fuori range producono esito non raggiungibile.

La verifica viene effettuata aprendo un Socket verso host e porta con timeout configurabile. Una connessione riuscita produce esito RAGGIUNGIBILE; IOException o validazione fallita producono esito NON RAGGIUNGIBILE con dettaglio in log.

### 6.4 Aggiornamento stato endpoint

Per ogni endpoint viene eseguito un update parametrico:

```sql
UPDATE TOPSENTINEL_ENDPOINT
SET ATTIVO = ?,
    LAST_UPDATE = SYSDATE,
    ULTIMA_VOLTA_RAGGIUNGIBILE = CASE
        WHEN ? = 1 THEN SYSDATE
        ELSE ULTIMA_VOLTA_RAGGIUNGIBILE
    END
WHERE ID = ?
```

LAST_UPDATE viene sempre aggiornato. ULTIMA_VOLTA_RAGGIUNGIBILE viene aggiornata solo quando l'esito corrente e' positivo.

### 6.5 Politica alert mail

| Condizione | Azione mail | Motivo registrato |
| --- | --- | --- |
| Endpoint raggiungibile | Nessun invio | NO |
| Endpoint non raggiungibile e ATTIVO precedente = 1 | Invio immediato | CAMBIO_STATO_ATTIVO_1_A_0 |
| Endpoint non raggiungibile e minuto DB tra 1 e 9 | Invio promemoria | PROMEMORIA_MINUTO_<minuto> |
| Endpoint non raggiungibile fuori finestra promemoria | Nessun invio | NO |

La finestra promemoria e' definita dalle costanti REMINDER_START_MINUTE = 1 e REMINDER_END_MINUTE = 9. La scelta usa il minuto di SYSDATE letto dal database.

### 6.6 Logging e riepilogo

Ogni endpoint produce una riga di log contenente:

- ID;
- endpoint originale;
- porta;
- host normalizzato;
- stato ATTIVO precedente;
- esito del controllo;
- nuovo valore ATTIVO;
- decisione mail;
- dettaglio tecnico;
- tempo di controllo in millisecondi.

Al termine del ciclo vengono stampati i conteggi complessivi:

- endpoint raggiungibili;
- endpoint non raggiungibili;
- record aggiornati;
- mail inviate;
- mail non inviate.

## 7. Configurazione

### 7.1 Parametri CLI

| Parametro | Descrizione |
| --- | --- |
| --timeout-ms | Timeout connessione socket verso endpoint Gallery, espresso in millisecondi. Deve essere maggiore di zero. |
| --db-driver | Classe driver JDBC da caricare. Default Oracle. |
| --db-url | URL JDBC del database. |
| --db-user | Utente database. |
| --db-password | Password database. Dato riservato: non deve essere riportato in log o documenti operativi. |
| --mail-endpoint | URL del servizio SOAP TopMail.asmx. |
| --mail-log-dir | Cartella in cui scrivere il log giornaliero degli invii mail. |

Esempi di avvio:

```text
java -jar TopSentinelMailGallery.jar
java -jar TopSentinelMailGallery.jar --timeout-ms=3000
java -jar TopSentinelMailGallery.jar --db-url=... --db-user=... --db-password=...
```

### 7.2 Valori di default

| Chiave | Valore default |
| --- | --- |
| db-driver | oracle.jdbc.driver.OracleDriver |
| db-url | jdbc:oracle:thin:@topgate1_sfe:1534/TOPGATE1 |
| db-user | TOPDBF |
| db-password | <mascherata> |
| timeout-ms | 5000 |
| mail-endpoint | http://topmail:9096/TopMail.asmx |
| mail-log-dir | \\\\pvtopesb\\e$\\TopSentinelMailGallery\\logs |

### 7.3 Parametri email

| Parametro interno | Valore / comportamento |
| --- | --- |
| Mittente | topsentinel@asl5.liguria.it |
| Destinatario attivo | manuel.manzotti@asl5.liguria.it |
| CCN | Vuoto |
| Oggetto | TopSentinel - Gallery non raggiungibile |
| Corpo | Messaggio sintetico di endpoint Gallery non raggiungibile |
| SOAPAction | http://tempuri.org/InvioMailConCCN |
| typeBody | 1 |

### 7.4 Parametri logging

Il batch run-topsentinel-mail-gallery.cmd crea, se assente, la cartella logs sotto la directory di esecuzione e accoda stdout/stderr nel file logs\TopSentinelMailGallery.log.

Il log degli invii mail riusciti viene scritto nella cartella configurata da --mail-log-dir, con file giornaliero in formato yyyyMMdd.txt e righe in formato HH:mm:ss | testo.

### 7.5 Gestione della configurazione

Gli override possono essere aggiunti agli argomenti del task schedulato o passati direttamente al batch. Il batch inoltra tutti gli argomenti ricevuti al JAR.

Esempio Task Scheduler:

```text
Program/script:
  C:\Batch\TopSentinelMailGallery\run-topsentinel-mail-gallery.cmd

Start in:
  C:\Batch\TopSentinelMailGallery

Arguments:
  --timeout-ms=5000 --db-url=jdbc:oracle:thin:@HOST:PORT/SERVICE --db-user=USER --db-password=PASSWORD --mail-endpoint=http://topmail:9096/TopMail.asmx --mail-log-dir=\\pvtopesb\e$\TopSentinelMailGallery\logs
```

## 8. Gestione errori e logging

### 8.1 Principi generali

Il logging principale avviene su standard output e standard error. In esercizio il batch reindirizza entrambi nel file logs\TopSentinelMailGallery.log sotto la cartella di distribuzione.

Gli errori vengono gestiti in modo coerente con il loro impatto:

- gli errori di configurazione impediscono l'avvio del controllo;
- gli errori SQL bloccano l'intera esecuzione;
- gli endpoint non raggiungibili sono esiti applicativi gestiti per singola riga;
- gli errori di invio mail vengono registrati ma non interrompono l'elaborazione degli altri endpoint;
- la mancata scrittura del log mail non invalida l'invio gia' riuscito.

### 8.2 Classificazione errori

| Evento | Gestione |
| --- | --- |
| Parametro non valido | Messaggio su stderr, stampa usage, exit code 2. |
| Driver JDBC non trovato | SQLException con dettaglio driver mancante. |
| Errore connessione DB o query | Stack trace su stderr, exit code 1. |
| Endpoint non raggiungibile | Update ATTIVO=0, eventuale mail secondo policy. |
| Porta non valida | Esito non raggiungibile con dettaglio "porta non valida". |
| Host vuoto o non valido | Esito non raggiungibile con dettaglio dedicato. |
| Invio mail KO | Errore su stderr con response abbreviata. |
| Mail inviata ma log non scritto | Warning su stderr; l'invio resta considerato riuscito. |

### 8.3 Meccanismi di resilienza

Il processo non implementa retry automatici sulle verifiche socket o sugli invii mail. La resilienza e' ottenuta tramite esecuzione schedulata periodica e tramite gestione puntuale degli errori per singolo endpoint.

La logica di alert limita il rumore operativo: viene inviata una mail immediata al cambio stato da ATTIVO=1 a ATTIVO=0 e, se l'endpoint resta non raggiungibile, vengono emessi promemoria solo nella finestra dei minuti 1-9 dell'ora database.

### 8.4 Struttura dei log

La riga di dettaglio endpoint segue la struttura:

```text
ID=<id> ENDPOINT=<endpoint> PORTA=<porta> HOST=<host> ATTIVO_PRECEDENTE=<valore> ESITO=<esito> ATTIVO=<0|1> MAIL=<decisione> DETTAGLIO=<dettaglio> TEMPO_MS=<ms>
```

Il riepilogo finale contiene:

```text
Controllo completato.
Raggiungibili: <numero>
Non raggiungibili: <numero>
Record aggiornati: <numero>
Mail inviate: <numero>
Mail non inviate: <numero>
```

### 8.5 Livelli di logging

Il sistema non usa un framework di logging esterno. Per convenzione:

- standard output contiene avvio, configurazione non sensibile, dettagli endpoint e riepilogo;
- standard error contiene parametri non validi, errori SQL, errori invio mail e warning di scrittura log.

## 9. Sicurezza e manutenibilita

Il sistema non espone endpoint web e non accetta input utente interattivo. La superficie principale e' costituita da configurazione runtime, accesso database, servizio mail, rete verso endpoint e file system di log.

Misure e raccomandazioni:

- trattare le credenziali database come segreto operativo;
- evitare di riportare password in documentazione, log o screenshot;
- preferire parametri esterni o configurazione protetta rispetto a credenziali hardcoded;
- limitare i permessi dell'utenza schedulata alle sole risorse necessarie;
- verificare i permessi sulla share del log mail;
- presidiare la raggiungibilita del servizio TopMail;
- mantenere il pacchetto precedente per rollback rapido;
- introdurre test automatici sulle funzioni di normalizzazione host e decisione alert in caso di evoluzione del codice.

Il codice utilizza PreparedStatement per l'update dello stato e costruisce l'envelope SOAP effettuando escaping XML sui campi inviati al servizio TopMail.

## 10. Requisiti di sistema

| Area | Requisito |
| --- | --- |
| Sistema operativo | Server Windows compatibile con Java 8 e Windows Task Scheduler. |
| Java | JDK/JRE 1.8, coerente con javac.source=1.8 e javac.target=1.8. |
| Database | Oracle raggiungibile tramite JDBC thin e credenziali abilitate su TOPSENTINEL_ENDPOINT. |
| Rete | Connettivita verso host Gallery, porta configurata, topmail:9096 e share log. |
| File system | Permessi di lettura sul JAR/lib e scrittura su logs locale e mail-log-dir. |
| Build | Progetto NetBeans Java Application con build Ant. |

## 11. Deployment

La distribuzione NetBeans richiede il JAR applicativo, la cartella lib con le dipendenze e il batch di avvio. Non e' sufficiente copiare il solo JAR, perche' le librerie JDBC sono referenziate dal classpath della distribuzione.

Contenuto da copiare sul server:

- TopSentinelMailGallery.jar;
- lib\;
- run-topsentinel-mail-gallery.cmd.

Percorso consigliato dal README server:

```text
C:\Batch\TopSentinelMailGallery
```

Il file TopSentinelMailGallery-server.zip contiene il pacchetto server gia' predisposto.

| Fase | Attivita |
| --- | --- |
| Build | Eseguire clean/dist da NetBeans o Ant e verificare la presenza del JAR in dist. |
| Copia | Copiare JAR, lib e CMD nella cartella server. |
| Configurazione task | Impostare Program/script, Start in e Arguments nel Task Scheduler. |
| Permessi | Verificare accesso DB, rete, servizio mail e scrittura log. |
| Smoke test | Eseguire manualmente java -jar o il CMD con timeout ridotto e controllare il log. |
| Rollback | Conservare il pacchetto precedente e ripristinare cartella JAR/lib/CMD in caso di anomalia. |

## 12. Ambiente di esercizio

In esercizio l'applicazione viene normalmente avviata dal Task Scheduler. Il batch imposta la working directory alla cartella del comando, usa JAVA_HOME se valorizzato, crea la cartella logs se assente e accoda l'output al file logs\TopSentinelMailGallery.log.

Contenuto del batch di avvio:

```bat
@echo off
setlocal
cd /d "%~dp0"
set "JAVA_EXE=java"
if defined JAVA_HOME set "JAVA_EXE=%JAVA_HOME%\bin\java.exe"
if not exist "%~dp0logs" mkdir "%~dp0logs"
"%JAVA_EXE%" -jar "%~dp0TopSentinelMailGallery.jar" %* >> "%~dp0logs\TopSentinelMailGallery.log" 2>&1
exit /b %ERRORLEVEL%
```

Il presidio operativo dovrebbe controllare periodicamente:

- assenza di errori SQL;
- numero di endpoint non raggiungibili;
- esito degli invii mail;
- crescita dei file di log;
- disponibilita della share configurata per il log mail;
- coerenza tra schedulazione attesa ed effettive righe di log prodotte.

## 13. Troubleshooting

| Sintomo | Verifiche consigliate |
| --- | --- |
| Errore "Driver JDBC non trovato" | Verificare presenza ojdbc8.jar in lib e classpath della distribuzione. |
| Errore connessione database | Verificare db-url, credenziali, DNS/servizio Oracle, firewall e disponibilita database. |
| Tutti gli endpoint non raggiungibili | Verificare rete dal server, risoluzione host, porta configurata e timeout. |
| Alcuni endpoint non raggiungibili | Verificare singolo host, porta, firewall locale/remoto e stato del servizio Gallery. |
| Mail non inviata | Verificare raggiungibilita http://topmail:9096/TopMail.asmx, response SOAP e autorizzazioni del servizio. |
| Log mail non scritto | Verificare esistenza e permessi della share configurata in --mail-log-dir. |
| Task schedulato non produce log | Verificare Start in, utenza di esecuzione, JAVA_HOME e permessi sulla cartella logs. |
| Invii mail troppo frequenti | Verificare valore ATTIVO precedente e frequenza di schedulazione del task nella finestra minuti 1-9. |

## 14. Allegati

### 14.1 Comandi utili

```text
java -jar TopSentinelMailGallery.jar --help
java -jar TopSentinelMailGallery.jar --timeout-ms=5000
run-topsentinel-mail-gallery.cmd --timeout-ms=5000
```

### 14.2 File principali

| Percorso | Descrizione |
| --- | --- |
| src/TopSentinelMailGallery/TopSentinelMailGallery.java | Main applicativo e logica di controllo. |
| src/TopSentinelMailGallery/TopMailService.java | Client SOAP TopMail. |
| src/TopSentinelMailGallery/DataService.java | Gestione connessione database. |
| dist/run-topsentinel-mail-gallery.cmd | Batch di esecuzione server. |
| dist/README-SERVER.txt | Promemoria operativo di deploy. |
| TopSentinelMailGallery-server.zip | Pacchetto server generato. |

### 14.3 Tabella TOPSENTINEL_ENDPOINT

| Campo | Uso applicativo |
| --- | --- |
| ID | Identificativo tecnico della riga, usato nella clausola WHERE dell'update. |
| ENDPOINT | Host o URL dell'endpoint Gallery da controllare. |
| PORTA | Porta TCP verso cui aprire la connessione socket. |
| ATTIVO | Stato precedente e corrente: 1 raggiungibile, 0 non raggiungibile. |
| LAST_UPDATE | Aggiornato a SYSDATE a ogni controllo. |
| ULTIMA_VOLTA_RAGGIUNGIBILE | Aggiornato a SYSDATE solo quando il controllo corrente e' positivo. |

### 14.4 Query principali

```sql
SELECT SYSDATE FROM DUAL;
```

```sql
SELECT * FROM TOPSENTINEL_ENDPOINT;
```

```sql
UPDATE TOPSENTINEL_ENDPOINT
SET ATTIVO = ?,
    LAST_UPDATE = SYSDATE,
    ULTIMA_VOLTA_RAGGIUNGIBILE = CASE
        WHEN ? = 1 THEN SYSDATE
        ELSE ULTIMA_VOLTA_RAGGIUNGIBILE
    END
WHERE ID = ?;
```
