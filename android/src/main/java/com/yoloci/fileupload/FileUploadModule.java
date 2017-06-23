package com.yoloci.fileupload;

import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;

import com.facebook.react.bridge.Arguments;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.bridge.ReadableMapKeySetIterator;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.Callback;

import java.io.DataInputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.DataOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;

import com.facebook.react.bridge.WritableMap;
import java.io.FileInputStream;
import java.nio.charset.Charset;

import org.json.JSONObject;

public class FileUploadModule extends ReactContextBaseJavaModule {

    @Override
    public String getName() {
        return "FileUpload";
    }

    public FileUploadModule(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @ReactMethod
    public void upload(final ReadableMap options, final Callback callback) {
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary =  "*****";

        String uploadUrl = options.getString("uploadUrl");
        String method;
        if (options.hasKey("method")) {
            method = options.getString("method");
        } else {
            method = "POST";
        }

        ReadableMap headers = options.getMap("headers");
        ReadableArray files = options.getArray("files");
        ReadableMap fields = options.getMap("fields");

        HttpURLConnection connection = null;
        DataOutputStream outputStream = null;
        DataInputStream inputStream = null;
        URL connectURL = null;
        FileInputStream fileInputStream = null;

        int bytesRead, bytesAvailable, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1*1024*1024;

        try {

            connectURL = new URL(uploadUrl);

            connection = (HttpURLConnection) connectURL.openConnection();

            // Allow Inputs &amp; Outputs.
            connection.setDoInput(true);
            connection.setDoOutput(true);
            connection.setUseCaches(false);

            connection.setRequestMethod(method);

            // set headers
            ReadableMapKeySetIterator iterator = headers.keySetIterator();
            while (iterator.hasNextKey()) {
                String key = iterator.nextKey();
                connection.setRequestProperty(key, headers.getString(key));
            }

            connection.setRequestProperty("Connection", "Keep-Alive");
            connection.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

            outputStream = new DataOutputStream( connection.getOutputStream() );

            // set fields
            ReadableMapKeySetIterator fieldIterator = fields.keySetIterator();
            while (fieldIterator.hasNextKey()) {
                outputStream.write(getBytes(twoHyphens + boundary + lineEnd));

                String key = fieldIterator.nextKey();
                outputStream.write(getBytes("Content-Disposition: form-data; name=\"" + key +  "\"" + lineEnd + lineEnd));
                outputStream.write(getBytes(fields.getString(key)));
                outputStream.write(getBytes(lineEnd));
            }


            for (int i = 0; i < files.size(); i++) {

                ReadableMap file = files.getMap(i);
                String name = file.getString("name");
                String filename = file.getString("filename");
                String filepath = getRealFilePath(file.getString("filepath"));
                fileInputStream = new FileInputStream(filepath);

                outputStream.write(getBytes(twoHyphens + boundary + lineEnd));
                outputStream.write(getBytes("Content-Disposition: form-data; name=\"" + (name == null ? filename : name) + "\"; filename=\"" + filename + "\"" + lineEnd));
                outputStream.write(getBytes("Content-Type: image/jepg" + lineEnd));
                outputStream.write(getBytes(lineEnd));

                bytesAvailable = fileInputStream.available();
                bufferSize = Math.min(bytesAvailable, maxBufferSize);
                buffer = new byte[bufferSize];

                // Read file
                bytesRead = fileInputStream.read(buffer, 0, bufferSize);

                while (bytesRead > 0) {
                    outputStream.write(buffer, 0, bufferSize);
                    bytesAvailable = fileInputStream.available();
                    bufferSize = Math.min(bytesAvailable, maxBufferSize);
                    bytesRead = fileInputStream.read(buffer, 0, bufferSize);
                }

                outputStream.write(getBytes(lineEnd));
            }

            outputStream.write(getBytes(twoHyphens + boundary + twoHyphens + lineEnd));

            // Responses from the server (code and message)

            int serverResponseCode = connection.getResponseCode();
            String serverResponseMessage = connection.getResponseMessage();
            if (serverResponseCode != 200) {
                fileInputStream.close();
                outputStream.flush();
                outputStream.close();
                callback.invoke("Error happened: " + serverResponseMessage, null);
            } else {
                BufferedReader br = new BufferedReader(new InputStreamReader(connection.getInputStream()));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = br.readLine()) != null) {
                    sb.append(line);
                }
                br.close();
                String data = sb.toString();
                JSONObject mainObject = new JSONObject();
                mainObject.put("data", data);
                mainObject.put("status", serverResponseCode);

                BundleJSONConverter bjc = new BundleJSONConverter();
                Bundle bundle = bjc.convertToBundle(mainObject);
                WritableMap map = Arguments.fromBundle(bundle);

                fileInputStream.close();
                outputStream.flush();
                outputStream.close();
                callback.invoke(null, map);
            }



        } catch(Exception ex) {
            callback.invoke("Error happened: " + ex.getMessage(), null);
        }
    }

    private byte[] getBytes(String content) {
        return content.getBytes(Charset.forName("UTF-8"));
    }

    private String getRealFilePath(String filePath) {
        filePath = filePath.replace("file://", "");
        if (filePath.startsWith("content")) {
            Uri contentUri = Uri.parse(filePath);

            String[] projection = {MediaStore.Images.Media.DATA};
            Cursor cursor = getReactApplicationContext().getContentResolver().query(
                    contentUri,
                    projection,
                    null,
                    null,
                    null
            );

            int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
            cursor.moveToFirst();

            filePath = cursor.getString(columnIndex);
        }
        return filePath;
    }
}
