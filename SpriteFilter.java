
public class SpriteFilter {
	// to spit out errors
	public SpriteFilter() {}
	static final SpriteFilter controller = new SpriteFilter();
	
	// format of snes 4bpp {row (r), bit plane (b)}
	// bit plane 0 indexed such that 1011 corresponds to 0123
	static final int BPPI[][] = {
			{0,0},{0,1},{1,0},{1,1},{2,0},{2,1},{3,0},{3,1},
			{4,0},{4,1},{5,0},{5,1},{6,0},{6,1},{7,0},{7,1},
			{0,2},{0,3},{1,2},{1,3},{2,2},{2,3},{3,2},{3,3},
			{4,2},{4,3},{5,2},{5,3},{6,2},{6,3},{7,2},{7,3}
	};
	
	public static void main(String[] args) {

	}
	
	/**
	 * Takes a sprite and turns it into 896 blocks of 8x8 pixels
	 * @param sprite
	 */
	public static byte[][][] sprTo8x8(byte[] sprite) {
		byte[][][] ret = new byte[896][8][8];
		
		// current block we're working on, each sized 32
		// start at -1 since we're incrementing at 0mod32
		int b = -1;
		// locate where in interlacing map we're reading from
		int g;
		for (int i = 0; i < sprite.length; i++) {
			// find interlacing index
			g = i%32;
			// increment at 0th index
			if (g == 0)
				b++;
			// row to look at
			int r = BPPI[g][0];
			// bit plane of byte
			int p = BPPI[g][1];
			
			// byte to unravel
			byte q = sprite[i];
			
			// run through the byte
			for (int x = 0; x < 8; x++) {
				// AND with 1 shifted to the correct plane
				boolean bitOn = (q & (1 << (7-x))) == 1;
				// if true, OR with that plane in index map
				if (bitOn)
					ret[b][r][x] |= (1 << (3-p));
			}
		}
		return ret;
	}
	
	/**
	 * Apply a filter based on a token.
	 * @param img - image map to screw up
	 * @param c - filter token
	 */
	public static byte[][][] filter(byte[][][] img, int c) {
		byte[][][] ret = img.clone();
		switch(c) {
			case 0:
				ret = staticFilter(ret);
				break;
			case 1:
				ret = swapFilter(ret);
		}
		
		return img;
	}
	
	/**
	 * Randomizes all non-trans pixels.
	 * @param img
	 */
	public static byte[][][] staticFilter(byte[][][] img) {
		for (int i = 0; i < img.length; i++)
			for (int j = 0; j < img[0].length; j++)
				for (int k = 0; k < img[0][0].length; k++) {
					if (img[i][j][k] != 0)
						img[i][j][k] = (byte) (Math.random() * 16);
				}
		return img;
	}

	/**
	 * Swaps indices with the other end; e.g. 0x1 swapped with 0xF, 0x2 swapped with 0xE, etc.
	 * Ignores trans pixels
	 */
	public static byte[][][] swapFilter(byte[][][] img) {
		for (int i = 0; i < img.length; i++)
			for (int j = 0; j < img[0].length; j++)
				for (int k = 0; k < img[0][0].length; k++) {
					if (img[i][j][k] != 0)
						img[i][j][k] = (byte) (16 - img[i][j][k]);
				}
		return img;
	}

	/**
	 * Turn the image into an array of 8x8 blocks.
	 * Assumes ABGR color space.
	 * <br><br>
	 * If a color matches an index that belongs to one of the latter 3 mails
	 * but does not match anything in green mail
	 * then it is treated as the color at the corresponding index of green mail.
	 * 
	 * @param pixels - aray of color indices
	 * @param pal - palette colors
	 * @return <b>byte[][][]</b> representing the image as a grid of color indices
	 */
	public static byte[][][] rasterTo8x8(byte[] pixels, int[] pal) {
		int dis = pixels.length/4;
		int largeCol = 0;
		int intRow = 0;
		int intCol = 0;
		int index = 0;

		// all 8x8 squares, read left to right, top to bottom
		byte[][][] eightbyeight = new byte[896][8][8];

		// read image
		for (int i = 0; i < dis; i++) {
			// get each color and get rid of sign
			// colors are stored as {A,B,G,R,A,B,G,R...}
			int b = (pixels[i*4+1]+256)%256;
			int g = (pixels[i*4+2]+256)%256;
			int r = (pixels[i*4+3]+256)%256;

			// convert to 9 digits
			int rgb = (1000000 * r) + (1000 * g) + b;

			// find palette index of current pixel
			for (int s = 0; s < pal.length; s++) {
				   if (pal[s] == rgb) {
					eightbyeight[index][intRow][intCol] = (byte) (s % 16); // mod 16 in case it reads another mail
					break;
				}
			}

			// count up square by square
			// at 8, reset the "Interior column" which we use to locate the pixel in 8x8
			// increments the "Large column", which is the index of the 8x8 sprite on the sheet
			// at 16, reset the index and move to the next row
			// (so we can wrap around back to our old 8x8)
			// after 8 rows, undo the index reset, and move on to the next super row
			intCol++;
			if (intCol == 8) {
				index++;
				largeCol++;
				intCol = 0;
				if (largeCol == 16) {
					index -= 16;
					largeCol = 0;
					intRow++;
					if (intRow == 8) {
						index += 16;
						intRow = 0;
					}
				}
			}
		}
		return eightbyeight;
	}

	/**
	 * Converts an index map into a proper 4BPP (SNES) byte map.
	 * @param eightbyeight - color index map
	 * @param pal - palette
	 * @param rando - palette indices to randomize
	 * @return new byte array in SNES4BPP format
	 */
	public static byte[] exportPNG(byte[][][] eightbyeight, byte[] palData) {
		// bit map
		boolean[][][] fourbpp = new boolean[896][32][8];

		for (int i = 0; i < fourbpp.length; i++) {
			// each byte, as per bppi
			for (int j = 0; j < fourbpp[0].length; j++) {
				for (int k = 0; k < 8; k++) {
					// get row r's bth bit plane, based on index j of bppi
					int row = BPPI[j][0];
					int plane = BPPI[j][1];
					int byteX = eightbyeight[i][row][k];
					// AND the bits with 1000, 0100, 0010, 0001 to get bit in that location
					boolean bitB = ( byteX & (1 << plane) ) > 0;
					fourbpp[i][j][k] = bitB;
				}
			}
		}

		// byte map
		// includes the size of the sheet (896*32) + palette data (0x78)
		byte[] bytemap = new byte[896*32+0x78];

		int k = 0;
		for (int i = 0; i < fourbpp.length; i++) {
			for (int j = 0; j < fourbpp[0].length; j++) {
				byte next = 0;
				// turn true false into byte
				for (boolean a : fourbpp[i][j]) {
					next <<= 1;
					next |= (a ? 1 : 0);
				}
				bytemap[k] = next;
				k++;
			}
		}
		// end 4BPP

		// add palette data, starting at end of sheet
		int i = 896*32-2;
		for (byte b : palData) {
			if (i == bytemap.length)
				break;
			bytemap[i] = b;
			i++;
		}
		return bytemap;
	}
}
