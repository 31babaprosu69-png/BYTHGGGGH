package pro.webhm.playerinteractions.client;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import javax.sound.sampled.AudioFormat;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.BitstreamException;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.JavaLayerException;
import javazoom.jl.decoder.SampleBuffer;
import net.minecraft.client.sounds.AudioStream;

public final class Mp3AudioStream implements AudioStream {
   private final Bitstream bits;
   private final Decoder decoder = new Decoder();
   private AudioFormat format;
   private byte[] pending = new byte[0];
   private int position;
   private boolean eof;
   private boolean closed;

   public Mp3AudioStream(InputStream input) throws IOException {
      this.bits = new Bitstream(new BufferedInputStream(input));

      try {
         if (!this.frame()) {
            throw new IOException("MP3 не содержит аудиокадров");
         }
      } catch (IOException var5) {
         try {
            this.close();
         } catch (IOException var4) {
         }

         throw var5;
      }
   }

   private boolean frame() throws IOException {
      if (this.eof) {
         return false;
      } else {
         boolean samples;
         try {
            Header header = this.bits.readFrame();
            if (header != null) {
               if (header.layer() != 3) {
                  throw new IOException("MP3 (MPEG Layer III) required");
               }

               SampleBuffer samplesx = (SampleBuffer)this.decoder.decodeFrame(header, this.bits);
               int rate = samplesx.getSampleFrequency();
               int channels = samplesx.getChannelCount();
               if (channels >= 1 && channels <= 2 && rate > 0) {
                  if (this.format == null) {
                     this.format = new AudioFormat((float)rate, 16, channels, true, false);
                  } else if (this.format.getChannels() != channels || this.format.getSampleRate() != (float)rate) {
                     throw new IOException("MP3 меняет формат внутри дорожки");
                  }

                  this.pending = new byte[samplesx.getBufferLength() * 2];
                  this.position = 0;
                  short[] pcm = samplesx.getBuffer();

                  for (int i = 0; i < samplesx.getBufferLength(); i++) {
                     this.pending[i * 2] = (byte)pcm[i];
                     this.pending[i * 2 + 1] = (byte)(pcm[i] >> 8);
                  }

                  return true;
               }

               throw new IOException("Invalid MP3 format");
            }

            this.eof = true;
            samples = false;
         } catch (RuntimeException | JavaLayerException var10) {
            throw new IOException("Could not decode MP3", var10);
         } finally {
            this.bits.closeFrame();
         }

         return samples;
      }
   }

   public AudioFormat getFormat() {
      return this.format;
   }

   public ByteBuffer read(int size) throws IOException {
      if (this.closed) {
         throw new IOException("MP3 закрыт");
      } else {
         int frameSize = this.format.getFrameSize();
         int requested = Math.max(frameSize, size / frameSize * frameSize);
         ByteBuffer out = ByteBuffer.allocateDirect(requested);

         while (out.hasRemaining() && (this.position != this.pending.length || this.frame())) {
            int count = Math.min(out.remaining(), this.pending.length - this.position);
            out.put(this.pending, this.position, count);
            this.position += count;
         }

         return out.flip();
      }
   }

   public void close() throws IOException {
      if (!this.closed) {
         this.closed = true;

         try {
            this.bits.close();
         } catch (BitstreamException var2) {
            throw new IOException(var2);
         }
      }
   }
}
