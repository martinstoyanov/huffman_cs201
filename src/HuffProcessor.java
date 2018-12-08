import java.util.*;

/**
 * Although this class has a history of several years,
 * it is starting from a blank-slate, new and clean implementation
 * as of Fall 2018.
 * <P>
 * Changes include relying solely on a tree for header information
 * and including debug and bits read/written information
 * 
 * @author Owen Astrachan
 */

public class HuffProcessor {

	public static final int BITS_PER_WORD = 8;
	public static final int BITS_PER_INT = 32;
	public static final int ALPH_SIZE = (1 << BITS_PER_WORD); 
	public static final int PSEUDO_EOF = ALPH_SIZE;
	public static final int HUFF_NUMBER = 0xface8200;
	public static final int HUFF_TREE  = HUFF_NUMBER | 1;

	private final int myDebugLevel;
	
	public static final int DEBUG_HIGH = 4;
	public static final int DEBUG_LOW = 1;
	
	public HuffProcessor() {
		this(0);
	}
	
	public HuffProcessor(int debug) {
		myDebugLevel = debug;
	}


	
	/**
	 * Decompresses a file. Output file must be identical bit-by-bit to the
	 * original.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */
	public void decompress(BitInputStream in, BitOutputStream out){
		
		int bits = in.readBits(BITS_PER_INT);
		if (bits != HUFF_TREE) {
			throw new HuffException("illegal header starts with"+bits);
		}
		
		HuffNode root = readTreeHeader(in);
		readCompressedBits(root,in,out);
		out.close();		
	}
	/**
	 * Reads through the tree recursively by cutting off parts of the input stream until there 
	 * are no more parts and returns the HuffNode that links to the new tree.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @return HuffNode
	 * 			  the entire tree with values stored in leaves and internal nodes throughout to traverse
	 */
	private HuffNode readTreeHeader(BitInputStream in) {
		int bit = in.readBits(1);
		
		if (bit == -1) {
			throw new HuffException("invalid tree !!!! bit :" + bit);
		}
		
		
		if (bit == 1) {
			int value = in.readBits(BITS_PER_WORD+1); //if it is not an internal node, it reads the bits and stores the value in this huff node
			return new HuffNode(value, 0, null, null); //finishes the recursion since you have reached an actual value
		} else { //if it is an internal node, it creates a new internal node and links the left and right subtrees recursively
			HuffNode left = readTreeHeader(in);
			HuffNode right = readTreeHeader(in);
			
			return new HuffNode(0,0,left,right);
		}		
	}
	/**
	 * reads a bit phrase, goes through the constructed tree. if you reach a leaf, write out the value stored in the leaf but otherwise keep moving through
	 *
	 * @param in
	 *            Buffered bit stream of the file to be decompressed.
	 * @param out 
	 * 			  Output that is written after reading through to a leaf
	 * @param root
	 * 			  the tree that you are using as a reference to decompress
	 */
	private void readCompressedBits(HuffNode root, BitInputStream in, BitOutputStream out) {
		HuffNode current = root;
		
		while (true) {
			int bits = in.readBits(1);
			if (bits == -1) {
				throw new HuffException("bad input, no PSEUDO_EOF");
			} else {
				if (bits == 1) current = current.myRight; //in the situation that you are not at a leaf, keep moving through
				else current = current.myLeft;
				
				if (current.myLeft == null && current.myRight == null) {
					if (current.myValue == PSEUDO_EOF) { //you have reached the iend
						break;
					} else {
						out.writeBits(BITS_PER_WORD, current.myValue);
						current = root;
					}
				}
			}
		}
	}
	
	/**
	 * Compresses a file. Process must be reversible and loss-less.
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 * @param out
	 *            Buffered bit stream writing to the output file.
	 */

	public void compress(BitInputStream in, BitOutputStream out) {
		
		int[] counts = readForCounts(in);
		HuffNode root = makeTreeFromCounts(counts);
		String[] codings = makeCodingsFromTree(root);
		
		out.writeBits(BITS_PER_INT, HUFF_TREE);
		writeHeader(root,out);
		
		in.reset();
		writeCompressedBits(codings, in, out);
		out.close();
		
	}
	/**
	 * Goes through the bit stream and records frequencies thruout
	 *
	 * @param in
	 *            Buffered bit stream of the file to be compressed.
	 */
	private int[] readForCounts(BitInputStream in)
	{
		int[] freq = new int[ALPH_SIZE + 1]; //array that contains everything plus pseudo_eos
		freq[PSEUDO_EOF] = 1;
		
		while(true) {
			int val = in.readBits(BITS_PER_WORD); //reads a single bit and increments its respective frequency
			if (val == -1) {
				break;
			}
			freq[val]++; //increment
		} 
		
		return freq;
	}
	/**
	 * Now that each letter has a respective weight, you can create a tree with long or short paths based
	 * on how frequent the letters are
	 *
	 * @param counts is the array of different letters and their respective frequencies
	 */
	private HuffNode makeTreeFromCounts(int[] counts) {
		
		PriorityQueue<HuffNode> pq = new PriorityQueue<>(); //ranks the inputted nodes based on their frequency
		int count = 0;
		while(count < counts.length) {
			if (counts[count] > 0) {
				pq.add(new HuffNode(count, counts[count], null, null)); //adds all the values in the form of huffnodes into the queue
			}
			count++;
		}
		
		while (pq.size() > 1) {
			
			HuffNode left = pq.remove();
			HuffNode right = pq.remove();
			
			HuffNode n = new HuffNode(0, left.myWeight + right.myWeight, left, right); //the smallest ones are grouped together like a greedy algo
			pq.add(n); //adds the new tree and rearranges the queue
			
		}
		
		HuffNode root = pq.remove();
		
		return root;
	}
	/**
	 * Now you are recording each of the codings or paths that you must traverse to find a particular
	 * value. Once you reach that value, you record the path that you went on to reach that leaf
	 *
	 * @param root is the tree that you must traverse through to find the leaves and store encodings
	 */
	private String[] makeCodingsFromTree(HuffNode root) {
		String[] encodings = new String[ALPH_SIZE + 1];
		codingHelper(root,"",encodings);
		return encodings;
	}
	private void codingHelper(HuffNode huffNode, String path, String[] encodings) {
		HuffNode left = huffNode.myLeft;
		HuffNode right = huffNode.myRight;
		if (left == null && right == null) {
			encodings[huffNode.myValue] = path; //base case since you can store the path for the respective value
		}
		
		if (left != null) {
			codingHelper(left, path + "0", encodings); //adds a 0 since you can go left
		}
		
		if (right != null) {
			codingHelper(right, path + "1", encodings); //adds a 1 since you can go right
		}
		
		
	}
	/**
	 *  if you are at a leaf, you write a single bit followed by the entire phrase that is inside that leaf
	 *  otherwise, you contineu writing single bits until you reach a leaf
	 *  you traverse recursively
	 *  
	 *  @param root is the tree that you are trying to go through
	 *  @param out is what you output whether that is an internal node or a leaf
	 */
	private void writeHeader(HuffNode root, BitOutputStream out) {
		if (root==null) {
			return;
		}
		
		if (root.myLeft == null && root.myRight == null) { //leaf case
			out.writeBits(1, 1);
			out.writeBits(9, root.myValue);
			return;
		} else {
			out.writeBits(1, 0);
			writeHeader(root.myLeft, out);
			writeHeader(root.myRight, out);
		}
		
	}
	/**
	 *  while there are still bits, keep reading the set bit phrase size
	 *  
	 *  @param out is what you output whether that is an internal node or a leaf
	 *  @param in
	 *            Buffered bit stream of the file to be compressed.
	 *  @param codings the associated coding with each letter is stored in here
	 */
	private void writeCompressedBits(String[] codings, BitInputStream in, BitOutputStream out) {
		
		int bitin = in.readBits(BITS_PER_WORD);
		while(bitin != -1) { //as long as there are still bits left to read
			String code = codings[bitin];
			out.writeBits(code.length(), Integer.parseInt(code, 2)); //adds each encoding to the compressed bit writing
			bitin = in.readBits(BITS_PER_WORD);
		}
		String pseudo = codings[PSEUDO_EOF];
		out.writeBits(pseudo.length(), Integer.parseInt(pseudo, 2)); //adds the pseudo marker at the end to show that the code has ende
			
		}
			
}
