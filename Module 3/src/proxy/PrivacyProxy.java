
// the core of the proxy
// this code is not a piece of beauty... sorry for that...
// one day, when we're less in a hurry, we'll redo it properly...


package proxy;

import java.net.*;
import java.io.*;
import java.util.*;
import java.lang.System;

public abstract class PrivacyProxy extends Thread {
    private Socket socket = null;
    protected Boolean autoFlush = false;
    protected Boolean keepAlive = false;
    protected long threadId;
    protected StringBuilder logOutput = new StringBuilder();
    private static final int BUFFER_SIZE = 5*1024*1024;
    protected HashMap<String, String> requestHeaders = new HashMap<String, String>();
    protected HashMap<String, String> responseHeaders = new HashMap<String, String>();

    public PrivacyProxy(Socket socket, Boolean autoFlush) {
        super("ProxyThread");
        this.socket = socket;
        this.autoFlush = autoFlush;
        try {
            this.socket.setSoTimeout(0);
        }catch(Exception e) {
            log("Something went wrong while setting socket timeout: " + e.getMessage());
        }
        // log("New connection from client: " + socket.getRemoteSocketAddress());
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // Enhance your proxy by implementing the following three methods:
    //   - manipulateRequestHeaders
    //   - onRequest
    //   - onResponse
    //
    //////////////////////////////////////////////////////////////////////////

    protected abstract HashMap<String, String> onRequest(HashMap<String, String> headers, String url);

    // The number of valid bytes in the buffer is expressed by the inOctets variable
    protected abstract byte[] onResponse(byte[] originalBytes, String httpresponse);


    //////////////////////////////////////////////////////////////////////////
    //
    // Helper methods:
    //  - log:                  print debug output to stdout
    //  - printSafe:            print the contents of a byte array (in a safe manner)
    //
    //////////////////////////////////////////////////////////////////////////

    protected void log(String s){
        if(autoFlush) {
            System.out.println(String.format("<%4d> %s", threadId, s));
        } else {
            logOutput.append(String.format("<%4d> %s%n", threadId, s));
        }
    }

    protected void printSafe(byte[] b){
        log("[PS] " + new String(b).replaceAll("[^\\p{Graph}\\p{Space}]", "") + "\n");
    }

    //////////////////////////////////////////////////////////////////////////
    //
    // run() loop of proxy.PrivacyProxy, no need to edit anything below this comment block 
    //
    //////////////////////////////////////////////////////////////////////////

    public void run() {

        this.threadId = Thread.currentThread().getId();
        try {
            int inOctets;
            DataOutputStream toClient = new DataOutputStream(socket.getOutputStream());
            InputStream fromClient = socket.getInputStream();

            String inputLine, outputLine;
            int cnt = 0;
            String urlToCall = "";

            Socket connectionToServer;
            DataOutputStream toWeb = null;
            InputStream fromWeb = null;
            byte[] buffer = new byte[BUFFER_SIZE];
            inOctets = fromClient.read(buffer, 0, BUFFER_SIZE);     // TODO: this code assumes the entire request comes in in one read; nothing guarantees that!!!
            if (inOctets==-1) return;
            String request = "";
            int totalInOctets = inOctets;
            boolean dropped = false;
            String method = "";
            String altered = "";
            String firstLine = "";
                if (cnt == 0) {
                    request = new String(buffer, 0, inOctets);;
                    Scanner scanner = new Scanner(request);
                    int lineCnt = 0;
                    boolean headersDone = false;
                    while (scanner.hasNextLine()) {
                        String line = scanner.nextLine();
                        if (lineCnt == 0) {
                            // first line, contains HTTP method
                            String[] tokens = line.split(" ");
                            method = tokens[0];
                            urlToCall = tokens[1];
                            firstLine = line;
                            if(!method.equals("GET") && !method.equals("POST") && !method.equals("OPTIONS")) // && !method.equals("CONNECT")) 
                            {
                                log("Unsupported HTTP method '" + method + "', dropping request");
                                dropped = true;
                            }
                        } else if(!line.equals("")) {
                            if (!headersDone) {
                                try{
                                    String[] tokens = line.split(":", 2);
                                    requestHeaders.put(tokens[0], tokens[1].trim());
                                }catch(ArrayIndexOutOfBoundsException e){
                                    log("Error parsing line: " + line);
                                }
                            } else {
                                if (!method.equals("POST")) {
                                    log("unsupported scenario ...");
                                }
                            }
                        } else {
                            // headers done, but there is still content, should be POST
                            headersDone = true;
                        }
                        lineCnt++;
                    }

                    request = new String(buffer, 0, inOctets);;

                    requestHeaders = onRequest(requestHeaders, urlToCall);
                    if (requestHeaders == null) {
                        log("Dropped request");
                        dropped = true;
                    }

                    if (!dropped) {
                        try {
                            URL url = new URL(urlToCall);
                            String webserverHost = url.getHost();
                            String protocol = url.getProtocol();
                            int webserverPort = url.getPort();
                            if (webserverPort == -1) {
                                webserverPort = url.getDefaultPort();
                            }
                            //log("Connecting to " + webserverHost + " on port " + webserverPort);
                            connectionToServer = new Socket(webserverHost, webserverPort);
                            toWeb = new DataOutputStream(connectionToServer.getOutputStream());
                            fromWeb = connectionToServer.getInputStream();

                            String[] firstLineParts = firstLine.split(" ");
                            firstLineParts[1] = (new URL(urlToCall)).getPath();
                            firstLine = firstLineParts[0] + " " + firstLineParts[1] + " " +  firstLineParts[2];
                            altered = firstLine + "\r\n";
                        } catch (MalformedURLException e) {
                            log("Malformed URL: " + urlToCall);
                            log("buffer:");
                            printSafe(buffer);
                            dropped = true;
                        }
                    }
                }

                cnt++;

                //if(altered.substring(altered.length()-4).equals("\r\n\r\n")){
                //    requestHeaders = manipulateRequestHeaders(requestHeaders);
                //    altered = altered.split("\r\n")[0] + "\r\n";
                if (!dropped) {
                    for (String h : requestHeaders.keySet()) {
                        altered = altered.concat(String.format("%s: %s\r\n", h, requestHeaders.get(h)));
                    }
                }
                altered = altered.concat("\r\n");

                if(method.equals("POST")) {
                    try {
                       String originalPayload = request.split("\r\n\r\n")[1];
                       altered = altered.concat(originalPayload);
                    } catch (ArrayIndexOutOfBoundsException e) {};
                }

            if(!dropped){
                toWeb.write(altered.getBytes());
                toWeb.flush();
                //log("Proxy'd request to the real webserver, waiting for response");

                int cnt2 = 0;
                int contentLength   = 0;
                int contentSent     = 0;
                int sizeOfHeaders   = 0;
                boolean cached      = false;
                boolean moved       = false;
                boolean chunked     = false;
                boolean proxyDone   = false;
                buffer  = new byte[BUFFER_SIZE];

                responseHeaders = new HashMap<String, String>();
                String httpresponseline="";
                boolean headersDone = false;

                String s="";
                inOctets=0;
                while (true) {
                     inOctets += fromWeb.read(buffer, inOctets, BUFFER_SIZE-inOctets);
                     s = new String(buffer, 0, inOctets);
                     sizeOfHeaders = s.indexOf("\r\n\r\n");
                     if (sizeOfHeaders>=0) break;
                }
                sizeOfHeaders+=4;    // add length of the terminating linefeeds

                // now we have all headers, and perhaps a bit more, in our buffer (unless the headers in total are more than BUFFER_SIZE, then this fails :-( )
                Scanner scanner = new Scanner(s);
                while (scanner.hasNextLine() && !headersDone){
                    String line = scanner.nextLine();
                    if (line.startsWith("HTTP/")){
                        httpresponseline=line;
                        //log(line);
                        if(line.contains("200")){
                            //log("Request was succesful");
                        } else if(line.contains("301")){
                            //log("Page has moved");
                            moved = true;
                        } else if(line.contains("304")){
                            //log("Page not modified");
                            cached = true;
                        }
                    } else if (line.equals("")){
                        //log("Reponse: end of headers");
                        headersDone = true;
                    } else {
                        try{
                            String[] tokens = line.split(":", 2);
                            responseHeaders.put(tokens[0], tokens[1].trim());
                        }catch(ArrayIndexOutOfBoundsException e){
                            log("Could not split line: " + line);
                        }
                    }
                }

                //log("HTTP response from webserver complete, headers:");
                if(responseHeaders.containsKey("Content-Length")) {
                    try {
                        contentLength = Integer.parseInt(responseHeaders.get("Content-Length"));
                    } catch (NumberFormatException e) {
                        log("Could not parse Content-Length header: " + responseHeaders.get("Content-Length"));
                    }
                } else if (responseHeaders.containsKey("Transfer-Encoding")) {
                    chunked = responseHeaders.get("Transfer-Encoding").equals("chunked");
                }

                if (!chunked) {
                    while (inOctets<contentLength+sizeOfHeaders) {
                        inOctets += fromWeb.read(buffer, inOctets, BUFFER_SIZE-inOctets);
                    }
                } else {
                    while (true) {
                        inOctets += fromWeb.read(buffer, inOctets, BUFFER_SIZE-inOctets);

                        // now check whether the end of the buffer looks like the end-of-file of chunked transfer;
                        // this check is not perfect, it may be triggered accidentally (albeit very unlikely), but it saves us from having to track all
                        // chunks and their lengths, so for the purpose of this challenge, it's good enough...
                        // the end of the chunk looks like a newline, a hexadecimal representation of the number 0 (i.e., one or more '0' characters), and two more newlines
                        int j=inOctets-1;
                        if (j<7) continue;
                        if (buffer[j--]!='\n') continue;
                        if (buffer[j--]!='\r') continue;
                        if (buffer[j--]!='\n') continue;
                        if (buffer[j--]!='\r') continue;
                        if (buffer[j--]!='0') continue;
                        while (j>3 && buffer[j]=='0') j--;
                        if (buffer[j--]!='\n') continue;
                        if (buffer[j--]!='\r') continue;
                        break;
                    }
                }


                // now actually proxy the completed response to the client
                try {
                    byte[] correctlySizedBuffer = new byte[inOctets];
                    System.arraycopy(buffer, 0, correctlySizedBuffer, 0, inOctets);  // pity we need this copy; TODO: it would be better to use ArrayList data structure
                    byte[] alteredBytes = onResponse(correctlySizedBuffer, httpresponseline);
                    toClient.write(alteredBytes, 0, alteredBytes.length);
                    toClient.flush();
                }catch (IOException e) {
                    log("Connection to the client seems lost: " + e.getMessage());
                }
            }
            
            
            // Clean up!
            
            if (toClient != null) {
                toClient.close();
            }
            if (fromClient != null) {
                fromClient.close();
            }
            if (socket != null) {
                socket.close();
            }
            

        } catch (IOException e) {
            e.printStackTrace();
        }
        // log("Request done");
        // Printing buffer in case of autoFlush==false
        if(!autoFlush) {
            System.out.println(String.format("Thread %d:%n%s", Thread.currentThread().getId(), logOutput.toString()));
        }
    }
}
