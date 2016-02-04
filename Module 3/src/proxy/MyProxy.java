package proxy;

import java.net.*;
import java.io.*;
import java.util.*;

public class MyProxy extends PrivacyProxy {

    //////////////////////////////////////////////////////////////////////////
    //
    // Enhance your proxy by implementing the following three methods:
    //   - onRequest
    //   - onResponse
    //
    //////////////////////////////////////////////////////////////////////////

    protected HashMap<String, String> onRequest(HashMap<String, String> requestHeaders, String url){
    // This is the onRequest handler.
    // It will be executed whenever an HTTP request passes by.
    // Its arguments are a so-called HashMap containg all headers from the request, and a simple String containing the requested URL.
    // You can put code here to print the request headers, and to modify them.

        // let's simply print the requested URL, for a start that's enough:
        log("Request for: " + url);

        // if we want to print all the request headers , use the below code:
        // it does a for-loop over all headers

       if (url.contains("js")) {
    	   return null;
       }
       if (url.contains("clients1.google.com")) {
    	   return null;
       }
       if (url.contains("pagead")) {
    	   return null;
       }

       if (url.contains("plugin")) {
    	   return null;
       }
       
        // example code to do something if a certain requestheader is present:

        if (requestHeaders.containsKey("User-Agent")) {
        	requestHeaders.replace("User-Agent", "Mozilla/FOUT (compatible, MSIE FOUT, Windows NT FOUT; Trident/FOUT;  rv:FOUT) like FOUT");
        }
       
        if (requestHeaders.containsKey("Cache-Control")) {
        	requestHeaders.replace("Cache-Control", "no-store");
        }

        // example code to remove the  Creepyness  header:
        if (requestHeaders.containsKey("Cookie")) {
        requestHeaders.remove("Cookie");
        }
        if (requestHeaders.containsKey("Referer")) {
        requestHeaders.remove("Referer");
        }

        // example code to insert (or replace) the  Niceness  header:

        requestHeaders.put("Accept-Language","en-US;q=1.0");
        requestHeaders.put("DNT", "1");

        for (String header : requestHeaders.keySet()) {
            // within the for loop, the variable  header  contains the name of the header
            // and you can ask for the contents of that header using requestHeaders.get() .
            log("  REQ: " + header + ": " + requestHeaders.get(header));
        }
        // return the (manipulated) headers, or
        return requestHeaders;

        // alternatively, drop this request by returning null
        // return null;
    }


    protected byte[] onResponse(byte[] originalBytes, String httpresponse){
    // This is the onResponse handler.
    // It will be executed whenever an HTTP reply passes by.
    // Its arguments are the entire HTTP response (both headers and data) as a byte array, and the line containing the response code.
    // For your convenience, the response headers are also available as a HashMap called responseHeaders , but you can't modify it.
        log("Response: "+httpresponse);

        // if you want to (safely, i.e., without binary garbage) print the entire response, uncomment the following:
/*
        printSafe(originalBytes);
*/

        // if you want to modify the response, you can either modify the byte array directly,
        // or first convert it to a string and then modify that, _if_ you know for sure the response is in text form
        // (otherwise, a string doesn't make sense).
/*
        if (responseHeaders.containsKey("Content-Type") && responseHeaders.get("Content-Type").startsWith("text/html")) {
             String s = new String(originalBytes);
             String s2 = s.replaceAll("headers", " // if you want to (safely, i.e., without binary garbage) print the entire response, uncomment the following: // printSafe(originalBytes); // if you want to modify the response, you can either modify the byte array directly, // or first convert it to a string and then modify that, _if_ you know for sure the response is in text form // (otherwise, a string doesn't make sense). jaja");
             byte [] alteredBytes = s2.getBytes();
             log("L: "+originalBytes.length);
             responseLength = s2.length();
             log("L: "+alteredBytes.length);
             return alteredBytes;
        }
*/
        // return the original, unmodified array:
        return originalBytes;
    }

    
    // Constructor, no need to touch this
    public MyProxy(Socket socket, Boolean autoFlush) {
        super(socket, autoFlush);
    }
}
