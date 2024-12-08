package io.ipfs.api;

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.*;

public class Multipart {
    private final String boundary;
    private static final String LINE_FEED = "\r\n";
    private HttpURLConnection httpConn;
    private String charset;
    private OutputStream out;

    public Multipart(String requestURL, String charset) {
        this.charset = charset;

        boundary = createBoundary();

        try {
            URL url = new URL(requestURL);
            httpConn = (HttpURLConnection) url.openConnection();
            httpConn.setUseCaches(false);
            httpConn.setDoOutput(true);
            httpConn.setDoInput(true);
            httpConn.setRequestProperty("Expect", "100-continue");
            httpConn.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
            httpConn.setRequestProperty("User-Agent", "Java IPFS Client");
            httpConn.setChunkedStreamingMode(4096);
            out = httpConn.getOutputStream();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    public static String createBoundary() {
        Random r = new Random();
        String allowed = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        StringBuilder b = new StringBuilder();
        for (int i=0; i < 32; i++)
            b.append(allowed.charAt(r.nextInt(allowed.length())));
        return b.toString();
    }
    
    private Multipart append(String value) throws IOException {
        out.write(value.getBytes(charset));
        return this;
    }

    public void addFormField(String name, String value) throws IOException {
        append("--").append(boundary).append(LINE_FEED);
        append("Content-Disposition: form-data; name=\"").append(name).append("\"")
                .append(LINE_FEED);
        append("Content-Type: text/plain; charset=").append(charset).append(LINE_FEED);
        append(LINE_FEED);
        append(value).append(LINE_FEED);
        out.flush();
    }

    public void addSubtree(Path parentPath, NamedStreamable dir) throws IOException {
        Path dirPath = parentPath.resolve(dir.getName().get());
        addDirectoryPart(dirPath);
        for (NamedStreamable f: dir.getChildren()) {
            if (f.isDirectory())
                addSubtree(dirPath, f);
            else
                addFilePart("file", dirPath, f);
        }
    }

    public void addDirectoryPart(Path path) throws IOException {
        append("--").append(boundary).append(LINE_FEED);
        append("Content-Disposition: file; filename=\"").append(encode(path.toString())).append("\"").append(LINE_FEED);
        append("Content-Type: application/x-directory").append(LINE_FEED);
        append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        append(LINE_FEED);
        append(LINE_FEED);
        out.flush();
    }

    private static String encode(String in) {
        try {
            return URLEncoder.encode(in, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            throw new RuntimeException(e);
        }
    }

    public void addFilePart(String fieldName, Path parent, NamedStreamable uploadFile) throws IOException {
        Optional<String> fileName = uploadFile.getName().map(n -> encode(parent.resolve(n).toString().replace('\\','/')));
        append("--").append(boundary).append(LINE_FEED);
        if (!fileName.isPresent())
            append("Content-Disposition: file; name=\"").append(fieldName).append("\";").append(LINE_FEED);
        else
            append("Content-Disposition: file; filename=\"").append(fileName.get()).append("\";").append(LINE_FEED);
        append("Content-Type: application/octet-stream").append(LINE_FEED);
        append("Content-Transfer-Encoding: binary").append(LINE_FEED);
        append(LINE_FEED);
        out.flush();

        try {
            InputStream inputStream = uploadFile.getInputStream();
            byte[] buffer = new byte[4096];
            int r;
            while ((r = inputStream.read(buffer)) != -1)
                out.write(buffer, 0, r);
            out.flush();
            inputStream.close();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        append(LINE_FEED);
        out.flush();
    }

    public void addHeaderField(String name, String value) throws IOException {
        append(name + ": " + value).append(LINE_FEED);
        out.flush();
    }

    public String finish() throws IOException {
        StringBuilder b = new StringBuilder();

        append("--" + boundary + "--").append(LINE_FEED);
        out.flush();
        out.close();

        try {
            int status = httpConn.getResponseCode();
            if (status == HttpURLConnection.HTTP_OK) {
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        httpConn.getInputStream()));
                String line;
                while ((line = reader.readLine()) != null) {
                    b.append(line);
                }
                reader.close();
                httpConn.disconnect();
            } else {
                try {
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                            httpConn.getInputStream()));
                    String line;
                    while ((line = reader.readLine()) != null) {
                        b.append(line);
                    }
                    reader.close();
                } catch (Throwable t) {
                }
                throw new IOException("Server returned status: " + status + " with body: " + b.toString() + " and Trailer header: " + httpConn.getHeaderFields().get("Trailer"));
            }

            return b.toString();
        } catch (IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
