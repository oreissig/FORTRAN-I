package fortran.reader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;

import com.google.common.collect.PeekingIterator;

/**
 * The CardReader drives the initial read of a source
 * file and the basic parsing of the punched card format.
 * 
 * @author oreissig
 */
public interface CardReader {

	/**
	 * Reads the given String to create an according
	 * Iterable of Cards.
	 * 
	 * @param source source code
	 * @return cards
	 */
	public abstract PeekingIterator<Card> read(String source);

	/**
	 * Reads from the given InputStream to create an according
	 * Iterable of Cards.
	 * 
	 * @param input to read source code from
	 * @return cards
	 * @throws IOException
	 */
	public abstract PeekingIterator<Card> read(InputStream input)
			throws IOException;

	/**
	 * Reads from the given Reader to create an according
	 * Iterable of Cards.
	 * 
	 * @param reader to read source code from
	 * @return cards
	 * @throws IOException
	 */
	public abstract PeekingIterator<Card> read(Reader reader) throws IOException;

	/**
	 * Reads from the given Reader to create an according
	 * Iterable of Cards.
	 * 
	 * @param reader to read source code from
	 * @return cards
	 * @throws IOException
	 */
	public abstract PeekingIterator<Card> read(BufferedReader reader)
			throws IOException;

}