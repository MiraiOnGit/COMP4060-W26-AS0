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

        String urlString = "http://hri.cs.umanitoba.ca:5000";
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
        System.out.println("Saving Wav File");
        try (FileOutputStream fos = new FileOutputStream(filename)) {
            wavBuffer.writeTo(fos);
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
        player.playAudio(new ByteArrayInputStream(wavBuffer.toByteArray()));
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


    // nested class for the example, should be promoted to its own file
    public static class SotaAudioPlayer {

        private final String TARGET_MIXER = "CODEC [plughw:2,0]";

        private Mixer.Info mixer = null;

        public SotaAudioPlayer() {
            mixer = getMixerByName(TARGET_MIXER);
        }

        public void playAudio(ByteArrayInputStream wavbuffer) {  // file as .wav
            try(AudioInputStream audioStream = AudioSystem.getAudioInputStream(wavbuffer)) {
                AudioFormat format = audioStream.getFormat();
                int frameSize = format.getFrameSize();
                SourceDataLine line = AudioSystem.getSourceDataLine(format, mixer);
                System.out.println(format.isBigEndian());
                line.open(format);
                line.start();

                ByteArrayOutputStream pcmOut = new ByteArrayOutputStream();
                byte[] buf = new byte[1000*frameSize];
                int n;
                while ((n = audioStream.read(buf)) != -1) {
                    pcmOut.write(buf, 0, n);
                }
//
//                byte[] buffer = new byte[100*frameSize];
//                int bytesRead;
//                while ((bytesRead = audioStream.read(buffer)) != -1) {
//                    // audioStream.read always reads an integer number of frames. so any leftover bytes
//                    // mroe than a frame would be end of the file.
//                    int fullFrameBytes = bytesRead - bytesRead%frameSize;  // trim leftover extra bytes
//                    line.write(buffer, 0, fullFrameBytes);
//                }

                byte[] pcmBytes = (pcmOut.toByteArray());
                System.out.println(frameSize);
                final int FADE_FRAMES = (int)(format.getSampleRate() * 0.05); // 50ms
                System.out.println(FADE_FRAMES);

                int idxStart = pcmBytes.length - FADE_FRAMES*frameSize;
                int maxidx = idxStart;
                for (int i = 0; i < FADE_FRAMES; i++) {
                    float gain = 1.0f - (float) i / FADE_FRAMES;

                    int idx = idxStart + frameSize*i;
                    short sample = (short) (
                            (pcmBytes[idx + 1] << 8) | (pcmBytes[idx] & 0xff)
                    );

//                    System.out.println(idx+"  "+gain+"  sample before: "+sample);

                    sample = (short) (sample * gain);
//                    System.out.println("then "+sample);

                    pcmBytes[idx]     = (byte) (sample & 0xff);
                    pcmBytes[idx + 1] = (byte) ((sample >> 8) & 0xff);
                    maxidx = Math.max(maxidx, idx);
                }
                System.out.println("max idx] "+maxidx+ " "+pcmBytes.length);
                line.write(pcmBytes, 0, pcmBytes.length-(int)(format.getSampleRate() * 0.01)); // 10ms);
//                Arrays.fill(buffer, (byte) 0);
//                line.write(buffer, 0, buffer.length);

//                line.drain();
//                line.flush();
//                line.close();
                while(true);

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
            try(AudioInputStream audioStream = AudioSystem.getAudioInputStream(audioFile)) {
                AudioFormat format = audioStream.getFormat();
                SourceDataLine line = AudioSystem.getSourceDataLine(format, mixer);

                line.open(format);
                line.start();

                byte[] buffer = new byte[4096];
                int bytesRead;
                while ((bytesRead = audioStream.read(buffer)) != -1) {
                    line.write(buffer, 0, bytesRead);
                }

                line.drain();
                line.close();
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

