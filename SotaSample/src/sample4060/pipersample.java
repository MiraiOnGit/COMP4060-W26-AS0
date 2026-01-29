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
import java.util.Arrays;
import java.util.Locale;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.File;
import javax.sound.sampled.*;

public class pipersample {

    public static void main(String[] args) throws IOException {

        String filename = "speech.wav";

        // more voices available, check https://huggingface.co/rhasspy/piper-voices/tree/main
        // we can install if you want
        listPiperVoices("http://hri.cs.umanitoba.ca:5000");


        PiperRequest piperRequest = new PiperRequest(
                "http://hri.cs.umanitoba.ca:5000",
                "its a trap",
                null,  // or "en_GB-jenny_dioco-medium", or...
                1,
                0.667,
                0.8
        );
        ByteArrayOutputStream wavData = getPiperWavData(piperRequest);

        // once you get the wav data you can save it to a file for use / re-use, or just play it live
        // I recommend writing a simple cache object that saves if not generated, e.g., using a unique
        // identifier based on the text/settings.
        System.out.println("Saving Wav File");
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wavData.writeTo(fos);
        }
        System.out.println("File saved successfully");

        // use the Java system to play the file.
        // Sota has CPlayWave.PlayWave but this has some built in amplification and clips (distorts)
        // the responses from the piper server.
        System.out.println("Opening output audio");
        SotaAudioPlayer player = new SotaAudioPlayer();
        System.out.println("Playing Wav File");
        player.playAudio(filename);
        System.out.println("Finished playing");

        System.out.println("Playing Wav data from memory buffer");
        player.playAudio(new ByteArrayInputStream(wavData.toByteArray()));
        System.out.println("Finished playing from memory");

//        // you can play with the Sota built in CPlayWave system, but it clips as per prev. comment
//        System.out.println("Playing Wav File");
//        CPlayWave.PlayWave_wait(filename);
//        System.out.println("Finished playing");
//
//        System.out.println("Playing Wav data from memory buffer");
//        CPlayWave.PlayWave_wait(wavBuffer.toByteArray());
//        System.out.println("Finished playing from memory");

    }

    public static class PiperRequest {
        public final String urlString;
        public String textToGen;
        public double lengthScale;  // longer is slower
        public double noiseScale;  // natural variation
        public double noiseWScale; // phenome variation.
        public String voice; // use from listof available voices. Set to NULL to be default.

        public PiperRequest (String urlString, String textToGen, String voice, double lengthScale, double noiseScale, double noiseWScale) {
            this.urlString = urlString;
            this.textToGen = textToGen;
            this.lengthScale = lengthScale;
            this.noiseScale = noiseScale;
            this.noiseWScale = noiseWScale;
            this.voice = voice;
        }
    }

    public static ByteArrayOutputStream getPiperWavData(PiperRequest piperRequest) { return getPiperWavData(piperRequest, false);}
    public static ByteArrayOutputStream getPiperWavData(PiperRequest piperRequest, boolean debug) {
        ByteArrayOutputStream result = null;
        try {
            if (debug) System.out.println("Constructing the json RPC request");
            URL url = new URL(piperRequest.urlString);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setDoOutput(true);

            // JSON
            String voiceLine = piperRequest.voice == null ? "": "\"voice\": \""+piperRequest.voice+"\"";
            String jsonInputString = "{\"text\": \"" + piperRequest.textToGen + "\"," +   // see https://github.com/OHF-Voice/piper1-gpl/blob/main/docs/API_HTTP.md
                    voiceLine+", "+
                    "\"sample_rate\": 22050," +  // ensure match to Sota's working rate
                    "\"length_scale\": "+ piperRequest.lengthScale +"," +
                    "\"noise_scale\": "+ piperRequest.noiseScale +"," +
                    "\"noise_w_scale\": "+piperRequest.noiseWScale+"}";
            if (debug) System.out.println("Connecting...");
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInputString.getBytes("utf-8");
                if (debug) System.out.println("Sending Request...");
                os.write(input, 0, input.length);
            }

            if (debug) System.out.println("Reading response...");
            ByteArrayOutputStream wavBuffer = new ByteArrayOutputStream();
            try (InputStream in = conn.getInputStream()) {
                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = in.read(buffer)) != -1) {
                    wavBuffer.write(buffer, 0, bytesRead);
                }
            }
            if (debug) System.out.println("Response received successfully");
            result = wavBuffer;
        } catch (Exception e) {
            e.printStackTrace();
        }
        return result;
    }

    public static void listPiperVoices(String urlString) {
        ByteArrayOutputStream result = null;
        try {
            System.out.println("Constructing the json RPC request");
            URL url = new URL(urlString+"/voices");
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("Accept", "application/json");

            System.out.println("Connecting...");
            int status = conn.getResponseCode();
            InputStream in = (status >= 200 && status < 300)
                    ? conn.getInputStream()
                    : conn.getErrorStream();

            System.out.println("HTTP status: " + status);

            try (BufferedReader reader =
                         new BufferedReader(new InputStreamReader(in, "UTF-8"))) {

                String line;
                StringBuilder response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line).append('\n');
                }

                System.out.println(response.toString());
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    // nested class for the example, should be promoted to its own file
    public static class SotaAudioPlayer {

        private final String TARGET_MIXER = "CODEC [plughw:2,0]";

        private Mixer.Info mixer = null;

        public SotaAudioPlayer() {
            mixer = getMixerByName(TARGET_MIXER);
        }

        public void playAudio(ByteArrayInputStream wavbuffer) {  // file as .wav
            try(AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavbuffer)) {
                playAudio(audioStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void playAudio(AudioInputStream audioStream) {
            try {
                AudioFormat format = audioStream.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(format, mixer);

                line.open(format);
                line.start();
                byte[] buffer = new byte[4096];
                int bytesRead;
                int bytesWritten = 0;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                    bytesWritten+=bytesRead;
                }

                int extraNeeded = line.getBufferSize() - bytesWritten%line.getBufferSize();
                byte[] extraZeros = new byte[extraNeeded];
                Arrays.fill(extraZeros, (byte)0);
                line.write(extraZeros, 0, extraZeros.length);

                line.drain();
                line.close();

            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        public void playAudio(String filename) {  // file as .wav
            if(mixer == null) {
                System.out.println("Audio mixer not initialized");
                return;
            }

            File audioFile = new File(filename);
            try {
                AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile);
                playAudio(audioStream);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private Mixer.Info getMixerByName(String mixerName) {
            Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();
            for(Mixer.Info mixerInfo : mixerInfos)
                if(mixerInfo.getName().equals(mixerName))
                    return mixerInfo;
            return null;
        }
    }

}

