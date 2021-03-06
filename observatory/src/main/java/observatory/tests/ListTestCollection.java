package observatory.tests;

import java.io.File;
import java.io.IOException;
import java.util.Objects;

import observatory.internetnlAPI.config.testResult.TestResult;
import observatory.util.InvalidFormatException;

/**
 * Represents a collection of results.
 * 
 * @author Henrique Campos Ferreira
 */
public class ListTestCollection
{
    public final File resultsFolder;

    /**
     * Initializes a new collection of results from the specified directory.
     * 
     * @param resultsFolder - The directory with the results.
     */
    public ListTestCollection(File resultsFolder) throws IOException
    {
        this.resultsFolder = Objects.requireNonNull(resultsFolder);
        this.resultsFolder.mkdirs();
        if (!resultsFolder.isDirectory())
            throw new IOException("Invalid results location.");
    }

    /**
     * Save the results of list.
     * 
     * @param list
     * @throws IOException
     */
    public void saveListResults(ListTest list) throws IOException
    {
        list.getResults().save(getListResultsFile(list.getName()));
    }

    /**
     * Get the results of the specified list.
     * @param listName
     * @return The List Test results.
     * @throws IOException
     * @throws InvalidFormatException
     */
    public ListTest getListResults(String listName) throws IOException, InvalidFormatException
    {
        return ListTest.from(TestResult.fromFile(getListResultsFile(listName)));
    }

    /**
     * Checks if the results of the specified list are available.
     * @param listName
     * @return true if the results are available or false otherwise.
     */
    public boolean isListResultsAvailable(String listName)
    {
        return getListResultsFile(listName).isFile();
    }


    /**
     * Get the file associated with the specified list.
     * @param list
     * @return The file associated with the specified list.
     */
    private File getListResultsFile(String list)
    {
        return new File(this.resultsFolder, list + ".json");
    }
}
