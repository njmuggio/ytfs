package xyz.muggio.ytfs;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileAttribute;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;

/**
 * Created by nicho on 3/31/2016.
 */
public class YtFs {
	private static final Color[][][] blockColors = new Color[4][4][4];

	static {
		for (int r = 0; r < 4; r++) {
			for (int g = 0; g < 4; g++) {
				for (int b = 0; b < 4; b++) {
					blockColors[r][g][b] = makeColor(r, g, b);
				}
			}
		}
	}

	public static void main(String[] args) {
		if (args.length < 2) {
			printUsage();

			return;
		}

		int x = 256;
		int y = 144;
		int blockX = 32;
		int blockY = 36;
		boolean encode = false;
		String in = null;
		String out = null;
		String vid = null;

		for (int i = 0; i < args.length; i++) {
			switch (args[i]) {
				case "-e":
					encode = true;
					if (args.length <= i + 1) {
						printUsage();
						return;
					}
					in = args[++i];
					break;
				case "-d":
					encode = false;
					if (args.length <= i + 2) {
						printUsage();
						return;
					}
					vid = args[++i];
					out = args[++i];
					break;
				case "-x":
					if (args.length <= i + 2) {
						printUsage();
						return;
					}
					x = Integer.parseInt(args[++i]);
					blockX = Integer.parseInt(args[++i]);
					if (blockX <= 0 || x % blockX != 0) {
						System.out.println("blockX must be greater than 0 and a factor of x!");
						return;
					}
					break;
				case "-y":
					if (args.length <= i + 2) {
						printUsage();
						return;
					}
					y = Integer.parseInt(args[++i]);
					blockY = Integer.parseInt(args[++i]);
					if (blockY <= 0 || y % blockY != 0) {
						System.out.println("blockY must be greater than 0 and a factor of y!");
						return;
					}
					break;
			}
		}

		if (encode) encode(in, x, y, blockX, blockY);
		else decode(vid, out);
	}

	private static void encode(String inFile, int x, int y, int blockX, int blockY) {
		ByteBuffer buffer = null;
		try {
			buffer = ByteBuffer.wrap(Files.readAllBytes(new File(inFile).toPath()));
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}
		int size = buffer.array().length;

		List<Color> colors = new ArrayList<>();

		/*
		 * 3 channels
		 * 4 usable levels per channel
		 * 4 levels = 2 bits per channel
		 * 2*3 = 6 bits per block
		 */

		System.out.println("Encoding...");

		// Compute visual blocks
		while (buffer.hasRemaining()) {
			// metaBlock contains the data for four visual blocks
			byte[] metaBlock;
			if (buffer.remaining() >= 3) metaBlock = new byte[] {buffer.get(), buffer.get(), buffer.get()};
			else {
				metaBlock = new byte[3];
				for (int i = 0; buffer.hasRemaining(); i++)
					metaBlock[i] = buffer.get();
			}

//			// First six bits
//			colors.add(makeColor(
//					(metaBlock[0] & 0b00000011),
//					(metaBlock[0] & 0b00001100) >>> 2,
//					(metaBlock[0] & 0b00110000) >>> 4
//			));
//			// Second six bits
//			colors.add(makeColor(
//					(metaBlock[0] & 0b11000000) >>> 6,
//					(metaBlock[1] & 0b00000011),
//					(metaBlock[1] & 0b00001100) >>> 2
//			));
//			// Third six bits
//			colors.add(makeColor(
//					(metaBlock[1] & 0b00110000) >>> 4,
//					(metaBlock[1] & 0b11000000) >>> 6,
//					(metaBlock[2] & 0b00000011)
//			));
//			// Fourth six bits
//			colors.add(makeColor(
//					(metaBlock[2] & 0b00001100) >>> 2,
//					(metaBlock[2] & 0b00110000) >>> 4,
//					(metaBlock[2] & 0b11000000) >>> 6
//			));

			// First six bits
			colors.add(blockColors[(metaBlock[0] & 0b00000011)]
					[(metaBlock[0] & 0b00001100) >>> 2]
					[(metaBlock[0] & 0b00110000) >>> 4]
			);
			// Second six bits
			colors.add(blockColors[(metaBlock[0] & 0b11000000) >>> 6]
					[(metaBlock[1] & 0b00000011)]
					[(metaBlock[1] & 0b00001100) >>> 2]
			);
			// Third six bits
			colors.add(blockColors[(metaBlock[1] & 0b00110000) >>> 4]
					[(metaBlock[1] & 0b11000000) >>> 6]
					[(metaBlock[2] & 0b00000011)]
			);
			// Fourth six bits
			colors.add(blockColors[(metaBlock[2] & 0b00001100) >>> 2]
					[(metaBlock[2] & 0b00110000) >>> 4]
					[(metaBlock[2] & 0b11000000) >>> 6]
			);
		}

		buffer = null;
		System.gc();

		Path outPath = null;

		try {
			outPath = Files.createTempDirectory("ytfs-temp");
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		final File outDir = outPath.toFile();
//		if (!outDir.exists()) outDir.mkdir();

//		new File(outDir).mkdir();

		System.out.println("Generating frames...");

		IntStream.range(0, Math.max(25, (int) Math.ceil((double)colors.size() / (x / blockX * y / blockY)))).parallel().forEach(f -> {
			FileOutputStream fos = null;
			try {
				fos = new FileOutputStream(outDir.toString() + "/" + String.format("%09d", f + 1) + ".png");
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			BufferedImage out = new BufferedImage(x, y, BufferedImage.TYPE_INT_RGB);
			Graphics2D g = out.createGraphics();

			for (int by = 0; by < y / blockY; by++) {
				for (int bx = 0; bx < x / blockX; bx++) {
					if (f * (x / blockX) * (y / blockY) + (by * (x / blockX) + bx) < colors.size()) {
						g.setColor(colors.get(f * (x / blockX) * (y / blockY) + (by * (x / blockX) + bx)));
					}
					g.fillRect(bx * blockX, by * blockY, blockX, blockY);
				}
			}

			try {
				ImageIO.write(out, "png", fos);
				fos.close();
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}
		});

		BufferedImage headerFrame = new BufferedImage(x, y, BufferedImage.TYPE_INT_RGB);
		Graphics2D g = headerFrame.createGraphics();

		int height = y / 3;
		int width = x / 32;

		// Draw blockX
		for (int i = 0; i < 32; i++) {
			if ((blockX & (1 << i)) != 0) g.setColor(Color.WHITE);
			else g.setColor(Color.BLACK);
			g.fillRect(i * width, 0, width, height);
		}

		// Draw blockY
		for (int i = 0; i < 32; i++) {
			if ((blockY & (1 << i)) != 0) g.setColor(Color.WHITE);
			else g.setColor(Color.BLACK);
			g.fillRect(i * width, height, width, height);
		}

		// Draw file size
		for (int i = 0; i < 32; i++) {
			if ((size & (1 << i)) != 0) g.setColor(Color.WHITE);
			else g.setColor(Color.BLACK);
			g.fillRect(i * width, 2 * height, width, height);
		}

		try {
			FileOutputStream fos = new FileOutputStream(outDir.toString() + "/" + String.format("%09d", 0) + ".png");
			ImageIO.write(headerFrame, "png", fos);
			fos.close();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		System.out.println("Rendering...");

		try {
			Process p = Runtime.getRuntime().exec("./ffmpeg/bin/ffmpeg -y -nostdin -loglevel quiet -f image2 -i " + outDir + "/%09d.png -vcodec libx264 -b 5M " + inFile + ".avi");
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		System.out.println("Cleaning up...");

		deleteFolder(outDir);

		System.out.println("Complete!");
	}

	private static void decode(String vid, String out) {
		// Download video
		System.out.println("Downloading video '" + vid + "'...");
		try {
			Process p = Runtime.getRuntime().exec("./youtube-dl.exe -f bestvideo[ext=mp4] -o \"%(id)s.%(ext)s\" " + vid);
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		Path outPath = null;

		try {
			outPath = Files.createTempDirectory("ytfs-temp");
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		final File outDir = outPath.toFile();

		// Split video to individual frames
		System.out.println("Extracting frames...");
		try {
			Process p = Runtime.getRuntime().exec("./ffmpeg/bin/ffmpeg -y -nostdin -loglevel quiet -i " + vid + ".mp4 " + outDir.toString() + "/%09d.png");
			p.waitFor();
		} catch (IOException | InterruptedException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		// Decode frames
		System.out.println("Decoding frames...");

		// Decode header frame
		BufferedImage headerFrame = null;
		try {
			headerFrame = ImageIO.read(new File(outDir.toString() + "/000000001.png"));
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		int x = headerFrame.getWidth();
		int y = headerFrame.getHeight();
		int blockX = 0;
		int blockY = 0;
		int size = 0;

		// Read blockX
		for (int i = 0; i < 32; i++) {
			Color c = new Color(headerFrame.getRGB(i * x / 32 + x / 64, y / 6));
			if (colorDist(c, Color.WHITE) < colorDist(c, Color.BLACK)) {
				blockX |= 1 << i;
			}
		}

		// Read blockY
		for (int i = 0; i < 32; i++) {
			Color c = new Color(headerFrame.getRGB(i * x / 32 + x / 64, 3 * y / 6));
			if (colorDist(c, Color.WHITE) < colorDist(c, Color.BLACK)) {
				blockY |= 1 << i;
			}
		}

		// Read size
		for (int i = 0; i < 32; i++) {
			Color c = new Color(headerFrame.getRGB(i * x / 32 + x / 64, 5 * y / 6));
			if (colorDist(c, Color.WHITE) < colorDist(c, Color.BLACK)) {
				size |= 1 << i;
			}
		}

		new File(outDir.toString() + "/000000001.png").delete();

		byte[] bytes = new byte[size];
		int processed = 0;

		// Read every block
		List<Color> colors = new ArrayList<>();
		for (File f : outDir.listFiles()) {
			BufferedImage img = null;
			try {
				img = ImageIO.read(f);
			} catch (IOException e) {
				System.err.println(e.getMessage());
				System.exit(1);
			}

			for (int dy = 0; dy < y / blockY; dy++) {
				for (int dx = 0; dx < x / blockX; dx++) {
//					colors.add(nearestColor(new Color(img.getRGB(dx * blockX + blockX / 2, dy * blockY + blockY / 2))));
//					colors.add(nearestColor(averageColor(img, dx * blockX, dy * blockY, blockX, blockY)));
					colors.add(averageColor(img, dx * blockX, dy * blockY, blockX, blockY));
				}
			}
		}

		// Decode in chunks of four colors
		for (int i = 0; i < colors.size(); i += 4) {
			Color[] metaBlock = new Color[4];
			for (int j = i; j < i + 4; j++) {
				if (j < colors.size()) metaBlock[j - i] = colors.get(j);
				else metaBlock[j - i] = Color.BLACK;
			}

			byte[] decoded = decodeBlock(metaBlock);
			for (int j = 0; j < decoded.length; j++) {
				if (processed < size) bytes[processed++] = decoded[j];
			}
		}

		// Write output file
		System.out.println("Writing file '" + out + "'...");
		FileOutputStream fos;
		try {
			fos = new FileOutputStream(out);
			fos.write(bytes);
			fos.flush();
			fos.close();
		} catch (IOException e) {
			System.err.println(e.getMessage());
			System.exit(1);
		}

		System.out.println("Cleaning up...");
		deleteFolder(outDir);
		new File(vid + ".mp4").delete();

		System.out.println("Complete!");
	}

	private static Color makeColor(int x, int y, int z) {
		int r, g, b;
		switch (x) {
			case 0: r = 31; break;
			case 1: r = 95; break;
			case 2: r = 159; break;
			default: r = 223;
		}
		switch (y) {
			case 0: g = 31; break;
			case 1: g = 95; break;
			case 2: g = 159; break;
			default: g = 223;
		}
		switch (z) {
			case 0: b = 31; break;
			case 1: b = 95; break;
			case 2: b = 159; break;
			default: b = 223;
		}
		return new Color(r, g, b);
	}

	private static byte[] decodeBlock(Color[] colors) {
		if (colors.length != 4) {
			System.err.println("decodeBlock only works with arrays of 4 colors!");
			System.exit(1);
		}

		byte[] ret = new byte[3];

		byte[] decColor = new byte[4];
		for (int i = 0; i < decColor.length; i++) decColor[i] = decodeColor(colors[i]);

		ret[0] = (byte) ((decColor[1] & 0b00110000) << 2);		// R2
		ret[0] |= ((decColor[0] & 0b00000011) << 4);			// B1
		ret[0] |= (decColor[0] & 0b00001100);					// G1
		ret[0] |= ((decColor[0] & 0b00110000) >>> 4);			// R1
		ret[1] = (byte) ((decColor[2] & 0b00001100) << 4);		// G3
		ret[1] |= (decColor[2] & 0b00110000);					// R3
		ret[1] |= ((decColor[1] & 0b00000011) << 2);			// R2
		ret[1] |= ((decColor[1] & 0b00001100) >>> 2);			// G2
		ret[2] = (byte) ((decColor[3] & 0b00000011) << 6);		// R4
		ret[2] |= ((decColor[3] & 0b00001100) << 2);			// G4
		ret[2] |= ((decColor[3] & 0b00110000) >>> 2);			// B4
		ret[2] |= (decColor[2] & 0b00000011);					// R3

		return ret;
	}

	private static Color nearestColor(Color c) {
		int mr = 0, mg = 0, mb = 0;
		float minDist = Float.POSITIVE_INFINITY;

		for (int r = 0; r < 4; r++) {
			for (int g = 0; g < 4; g++) {
				for (int b = 0; b < 4; b++) {
					float d = colorDist(c, blockColors[r][g][b]);
					if (d < minDist) {
						minDist = d;
						mr = r;
						mg = g;
						mb = b;
					}
				}
			}
		}

		return blockColors[mr][mg][mb];
	}

	private static Color averageColor(BufferedImage img, int x, int y, int dx, int dy) {
		int r = 0, g = 0, b = 0;
		Map<Color, Integer> matches = new HashMap<>();
		for (int ly = y; ly < y + dy; ly++) {
			for (int lx = x; lx < x + dx; lx++) {
				Color c = new Color(img.getRGB(lx, ly));
				Color n = nearestColor(c);
				if (matches.containsKey(n)) matches.put(n, matches.get(n) + 1);
				else matches.put(n, 1);
//				r += c.getRed();
//				g += c.getGreen();
//				b += c.getBlue();
			}
		}
//		return new Color(r / (dx * dy), g / (dx * dy), b / (dx * dy));
		Color mode = null;
		int freq = 0;
		for (Color key : matches.keySet()) {
			if (matches.get(key) > freq) {
				freq = matches.get(key);
				mode = key;
			}
		}
		return mode;
	}

	private static byte decodeColor(Color c) {
		int mr = 0, mg = 0, mb = 0;
		float minDist = Float.POSITIVE_INFINITY;

		for (int r = 0; r < 4; r++) {
			for (int g = 0; g < 4; g++) {
				for (int b = 0; b < 4; b++) {
					float d = colorDist(c, blockColors[r][g][b]);
					if (d < minDist) {
						minDist = d;
						mr = r;
						mg = g;
						mb = b;
					}
				}
			}
		}

		return (byte) ((mr << 4) | (mg << 2) | mb);
	}

	private static void printUsage() {
		System.out.println("Encoding:");
		System.out.println("-e inputFile [-x frameWidth blockWidth] [-y frameY blockHeight]");
		System.out.println();
		System.out.println("Decoding:");
		System.out.println("-d videoId outputFile");
	}

	private static void deleteFolder(File folder) {
		File[] files = folder.listFiles();
		if(files!=null) { //some JVMs return null for empty dirs
			for(File f: files) {
				if(f.isDirectory()) {
					deleteFolder(f);
				} else {
					f.delete();
				}
			}
		}
		folder.delete();
	}

	private static float colorDist(Color c1, Color c2) {
		return (float) (Math.pow(c1.getRed() - c2.getRed(), 2)
								+ Math.pow(c1.getGreen() - c2.getGreen(), 2)
								+ Math.pow(c1.getBlue() - c2.getBlue(), 2));
	}
}
