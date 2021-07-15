package com.sasha2dx.linkparser;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class ParseUtils {

    public static InputStream getInputStreamFromUrl(String strUrl, String cookie) throws IOException {
        URL url = new URL(strUrl);
        HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
        urlConnection.addRequestProperty("User-Agent",
                "Mozilla/5.0 (X11; U; Linux i586; en-US; rv:1.7.3) Gecko/20040924"
                        + "Epiphany/1.4.4 (Ubuntu)");
        urlConnection.addRequestProperty("Cookie", cookie);
        return urlConnection.getInputStream();
    }


    public static String getPage(String strUrl) {
        return getPage(strUrl, "");
    }


    public static String getPage(String strUrl, String cookie) {
        StringBuilder response = new StringBuilder();
        try {
            BufferedReader br = new BufferedReader(new InputStreamReader(getInputStreamFromUrl(strUrl, cookie)));
            String strTemp = "";
            while (null != (strTemp = br.readLine())) {
                response.append(strTemp);
            }
        } catch (IOException e) {
            System.out.println("Error in time loading page: " + strUrl + ", error: " + e.toString());
        }
        return response.toString();
    }

}
