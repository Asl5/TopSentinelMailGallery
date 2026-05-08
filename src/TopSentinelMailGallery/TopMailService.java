package TopSentinelMailGallery;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class TopMailService {

    private static final String SOAP_ACTION = "\"http://tempuri.org/InvioMailConCCN\"";
    private static final int TYPE_BODY = 1;
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;

    private final String endpoint;

    public TopMailService(String endpoint) {
        this.endpoint = endpoint;
    }

    public MailResult sendMail(
            String mittente,
            String destinatario,
            String ccn,
            String oggetto,
            String testoMail) throws IOException {

        HttpURLConnection conn = null;
        try {
            URL url = new URL(endpoint);
            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "text/xml; charset=utf-8");
            conn.setRequestProperty("SOAPAction", SOAP_ACTION);
            conn.setConnectTimeout(CONNECT_TIMEOUT_MS);
            conn.setReadTimeout(READ_TIMEOUT_MS);
            conn.setDoOutput(true);

            String soapEnvelope = buildSoapEnvelope(mittente, destinatario, ccn, oggetto, testoMail);
            try (OutputStream os = conn.getOutputStream()) {
                os.write(soapEnvelope.getBytes(StandardCharsets.UTF_8));
            }

            int responseCode = conn.getResponseCode();
            InputStream is = responseCode >= 200 && responseCode < 300
                    ? conn.getInputStream()
                    : conn.getErrorStream();
            String responseBody = readResponseBody(is);
            boolean success = responseCode >= 200
                    && responseCode < 300
                    && !containsSoapFault(responseBody);

            return new MailResult(success, responseCode, responseBody);
        } finally {
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private String buildSoapEnvelope(
            String mittente,
            String destinatario,
            String ccn,
            String oggetto,
            String testoMail) {

        return "<?xml version=\"1.0\" encoding=\"utf-8\"?>"
                + "<soap:Envelope xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" "
                + "xmlns:soap=\"http://schemas.xmlsoap.org/soap/envelope/\">"
                + "<soap:Body>"
                + "<InvioMailConCCN xmlns=\"http://tempuri.org/\">"
                + "<mittente>" + escapeXml(mittente) + "</mittente>"
                + "<destinatario>" + escapeXml(destinatario) + "</destinatario>"
                + "<ccn>" + escapeXml(ccn) + "</ccn>"
                + "<oggetto>" + escapeXml(oggetto) + "</oggetto>"
                + "<testoMail>" + formatHtmlMailBody(testoMail) + "</testoMail>"
                + "<typeBody>" + TYPE_BODY + "</typeBody>"
                + "</InvioMailConCCN>"
                + "</soap:Body>"
                + "</soap:Envelope>";
    }

    private String readResponseBody(InputStream is) throws IOException {
        if (is == null) {
            return "";
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
        }
        return response.toString();
    }

    private boolean containsSoapFault(String responseBody) {
        String normalized = safe(responseBody).toLowerCase();
        return normalized.contains("<soap:fault")
                || normalized.contains("<faultcode>")
                || normalized.contains("<faultstring>");
    }

    private String formatHtmlMailBody(String value) {
        return escapeXml(value)
                .replace("\r\n", "\n")
                .replace('\r', '\n')
                .replace("\n", "&lt;br/&gt;");
    }

    private String escapeXml(String value) {
        String text = safe(value);
        StringBuilder escaped = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            switch (ch) {
                case '&':
                    escaped.append("&amp;");
                    break;
                case '<':
                    escaped.append("&lt;");
                    break;
                case '>':
                    escaped.append("&gt;");
                    break;
                case '"':
                    escaped.append("&quot;");
                    break;
                case '\'':
                    escaped.append("&apos;");
                    break;
                default:
                    escaped.append(ch);
                    break;
            }
        }
        return escaped.toString();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    public static class MailResult {

        private final boolean success;
        private final int responseCode;
        private final String responseBody;

        public MailResult(boolean success, int responseCode, String responseBody) {
            this.success = success;
            this.responseCode = responseCode;
            this.responseBody = responseBody;
        }

        public boolean isSuccess() {
            return success;
        }

        public int getResponseCode() {
            return responseCode;
        }

        public String getResponseBody() {
            return responseBody;
        }

        public String getSummary() {
            return "HTTP " + responseCode + (success ? " - OK" : " - KO");
        }
    }
}
