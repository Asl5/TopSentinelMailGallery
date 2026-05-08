TopSentinelMailGallery - deploy server

Contenuto da copiare sul server:
- TopSentinelMailGallery.jar
- lib\
- run-topsentinel-mail-gallery.cmd

Windows Task Scheduler:
Program/script:
  C:\Batch\TopSentinelMailGallery\run-topsentinel-mail-gallery.cmd

Start in:
  C:\Batch\TopSentinelMailGallery

Arguments, se vuoi sovrascrivere i default:
  --timeout-ms=5000 --db-url=jdbc:oracle:thin:@HOST:PORT/SERVICE --db-user=USER --db-password=PASSWORD --mail-endpoint=http://topmail:9096/TopMail.asmx --mail-log-dir=\\pvtopesb\e$\TopSentinelMailGallery\logs

Esecuzione diretta:
  java -jar TopSentinelMailGallery.jar --timeout-ms=5000

Nota: non copiare solo il JAR se usi questa distribuzione NetBeans, perche le librerie JDBC stanno in lib.
