package sample4060;

import jp.vstone.RobotLib.CPlayWave;
import marytts.LocalMaryInterface;
import marytts.MaryInterface;
import marytts.exceptions.MaryConfigurationException;
import marytts.exceptions.SynthesisException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Locale;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;


public class pipersample {

    public static void main(String[] args) throws IOException {

        String urlString = "http://cs.home:5000";
        String textToGen = "its a trap";
        String filename = "speech.wav";

        System.out.println("Constructing the json RPC request");
        URL url = new URL(urlString);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setDoOutput(true);

        // JSON
        String jsonInputString = "{\"text\": \""+textToGen+"\"," +   // see https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/API_HTTP.md
                                  "\"sample_rate\": 22050," +  // ensure match to Sota's working rate
                "\"length_scale\": 1.0," +  // longer is slower
                "\"noise_scale\": 0.7," +  // natural variation
                "\"noise_w_scale\": 0.8}";  // phenome variation.
        System.out.println("Connecting...");
        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInputString.getBytes("utf-8");
            System.out.println("Sending Request...");
            os.write(input, 0, input.length);
        }

        System.out.println("Reading response...");
        ByteArrayOutputStream wavBuffer = new ByteArrayOutputStream();
        try (InputStream in = conn.getInputStream()) {
            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = in.read(buffer)) != -1) {
                wavBuffer.write(buffer, 0, bytesRead);
            }
        }
        System.out.println("Response received successfully");
        byte[] data = wavBuffer.toByteArray();
        System.out.println("Saving Wav File");
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wavBuffer.writeTo(fos);
        }
        System.out.println("File saved successfully");

        System.out.println("Playing Wav File");
        CPlayWave.PlayWave_wait(filename);
        System.out.println("Finished playing");

        System.out.println("Playing Wav data from memory buffer");
        CPlayWave.PlayWave_wait(data);

        System.out.println("Finished playing from memory");
    }
}