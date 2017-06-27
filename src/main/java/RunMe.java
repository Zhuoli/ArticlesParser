import org.apache.commons.cli.*;
import org.openqa.selenium.*;
import org.openqa.selenium.firefox.FirefoxDriver;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import org.xml.sax.SAXException;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.io.*;
import java.net.*;
import java.util.Optional;

/**
 * Created by zhuoli on 6/18/17.
 */
public class RunMe {
    protected static String BASE_URL = "https://www.ncbi.nlm.nih.gov/pmc/?term=";
    public static void main(String[] args){
        Optional<CommandLine> cmd = RunMe.parseArguments(args);
        System.out.println("Input: " + Arrays.toString(args));

        if (!cmd.isPresent() || !cmd.get().hasOption(CommandConstant.KEY_WORD) || cmd.get().getOptionValue(CommandConstant.KEY_WORD).length()<1){
            System.err.println("ERROR: Please provide search key word as input parameter.");
            System.exit(1);
        }
        String keyword = cmd.get().getOptionValue(CommandConstant.KEY_WORD);
        String term = Arrays.stream(keyword.split(" ")).reduce("", (word, s) -> word + "+" +s).substring(1);
        String url = BASE_URL+term;

        System.out.println("Website to parse: " + url);


        String fileName = keyword + ".csv";

        Optional<Integer> maxPageNum = Optional.empty();

        if (cmd.isPresent() && cmd.get().hasOption(CommandConstant.KEY_WORD)){
            String numStr = cmd.get().getOptionValue(CommandConstant.KEY_WORD);
            try {
                maxPageNum = Optional.of(Integer.parseInt(numStr));
            }catch (NumberFormatException exc){
                System.err.println("Incorrect max page number.");
            }
        }

        new RunMe().parsePage(url, fileName, maxPageNum);
    }

    private static Optional<CommandLine> parseArguments(String[] args){

        // create Options object
        Options options = new Options();

        // add t option
        options.addOption(CommandConstant.KEY_WORD, true, "Key word for searching.");
        options.addOption(CommandConstant.PAGE_NUM, true, "Maximum number of pages to parse per query");
        CommandLineParser parser = new DefaultParser();
        try {
            return Optional.of(parser.parse(options, args));
        } catch (ParseException exc) {
            System.err.println("Arguments parse exception: " + exc.getMessage());
            return Optional.empty();
        }
    }

    public void parsePage(String url, String filename, Optional<Integer> maxPageNumOptional){

        // Clean up previous file and add csv header
        this.writeToFile(filename, Article.getHeaders(), false);

        System.setProperty("webdriver.gecko.driver","./src/main/resources/geckodriver");

        WebDriver driver = new FirefoxDriver();
        driver.get(url);

        int pageCount = 0;
        while(true) {
            pageCount++;
            if (maxPageNumOptional.isPresent() && pageCount >= maxPageNumOptional.get()){
                break;
            }

            List<Article> articleList = this.retrieveArticleInstance(driver, pageCount);
            for (Article article : articleList) {
                try {
                    String xml = this.retrieveCitationId(article.pmcId);
                    xml = xml.substring(xml.indexOf("<eLinkResult>"));
                    article.citationList = this.retrieveCitationIdsFromCsv(xml);
                    String[] keywordsAndAbstracts = this.retrieveKeyWordsAndAbstract(article.url);
                    article.keyWord = keywordsAndAbstracts[0];
                    this.writeToFile(String.format("abstract_%s.txt", article.pmcId), keywordsAndAbstracts[1], false);
                } catch (Exception exc) {
                    System.err.println("Error while querring citation webpage: " + exc.getMessage());
                    exc.printStackTrace();
                    article.pmcId = "NONE";
                }
            }
            StringBuilder sb = new StringBuilder();
            for(Article article : articleList) {
                sb.append(article.toString());
                sb.append(System.lineSeparator());
            }
            this.writeToFile(filename, sb.toString(), true);


            List<WebElement> nextPageClicks = driver.findElements(By.cssSelector("a[title='Next page of results']"));
            if(nextPageClicks.size()==0){
                break;
            }
            nextPageClicks.get(0).click();
        }

        driver.close();

        System.out.println("\nYOU ARE ALL SET \nParsing result saved at '" + filename + "' ENJOY!");
    }

    private boolean writeToFile(String fileName, String content, boolean isAppend){
        BufferedWriter bw = null;

        try {
            // APPEND MODE SET HERE
            bw = new BufferedWriter(new FileWriter(fileName, isAppend));
            bw.write(content);
            bw.flush();
        } catch (IOException ioe) {
            ioe.printStackTrace();
        } finally {                       // always close the file
            if (bw != null) try {
                bw.close();
            } catch (IOException ioe2) {
                // just ignore it
            }
        } // end try/catch/finally
        return true;
    }

    private List<String> retrieveCitationIdsFromCsv(String csvContent) throws ParserConfigurationException, IOException, SAXException {
        List<String> citationList = new LinkedList<>();

        // Create XML object and read values from the given path
        DocumentBuilder builder =
                DocumentBuilderFactory.newInstance().newDocumentBuilder();
        Document doc =
                builder.parse(new ByteArrayInputStream(csvContent.getBytes(StandardCharsets.UTF_8)));
        Element documentElement = doc.getDocumentElement();
        Element idElement =
                ((Element)  documentElement.getElementsByTagName("LinkSetDb").item(0));

        // No citation
        if (idElement == null){
            return citationList;
        }

        NodeList nodeList = idElement.getElementsByTagName("Id");

        for(int idx = 0; idx < nodeList.getLength(); idx++){
            String citation = nodeList.item(idx).getTextContent();
            if(!citation.isEmpty())
                citationList.add(citation);
        }
        return citationList;
    }

    private String[] retrieveKeyWordsAndAbstract(String url){
        String keywords = "";
        String abstracts = "";
        WebDriver driver = new FirefoxDriver();
        try {

            // Navigate to Google
            driver.get(url);
            WebElement abstractAndKeyworkdsElement = driver.findElement(By.cssSelector("div[id*='abstractidm']"));
            if (abstractAndKeyworkdsElement != null) {
                List<WebElement> elements = abstractAndKeyworkdsElement.findElements(By.cssSelector("div:only-child"));
                if (elements.size() >= 3) {
                    abstracts = elements.get(1).getText();
                    keywords = elements.get(2).getText();
                } else {
                    abstracts = abstractAndKeyworkdsElement.getText();
                }
            }
        }catch (Exception exc)
        {
            System.err.println("Error parsing abstracts at : " + url);
            System.err.println(exc.getMessage());

        }finally {
            driver.close();
        }

        return new String[]{keywords, abstracts};
    }

    private String retrieveCitationId(String citationId) throws IOException{
        String url =
                "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/elink.fcgi?dbfrom=pubmed&linkname=pubmed_pmc_refs&id=" + citationId;
        URL website = new URL(url);
        URLConnection connection = website.openConnection();
        BufferedReader in = new BufferedReader(
                new InputStreamReader(
                        connection.getInputStream()));

        StringBuilder response = new StringBuilder();
        String inputLine;

        while ((inputLine = in.readLine()) != null)
            response.append(inputLine);

        in.close();

        return response.toString();
    }


    private List<Article> retrieveArticleInstance(WebDriver driver, int pageNumber){
        List<Article> articleList = new LinkedList<>();

        try {
            List<WebElement> articleElementList = driver.findElements(By.cssSelector(".rprt"));
            for (WebElement webElement : articleElementList) {
                try {
                    Article article = new Article();
                    String title = webElement.findElement(By.cssSelector(".title")).getText();
                    String href = webElement.findElement(By.cssSelector(".title")).findElement(By.cssSelector("a")).getAttribute("href");
                    String authors = webElement.findElement(By.cssSelector(".desc")).getText();
                    article.title = title;
                    article.url = href;
                    article.authors = authors.split(",");

                    WebElement linkBlock = webElement.findElement(By.cssSelector(".links"));
                    List<WebElement> linkList = linkBlock.findElements(By.cssSelector("a"));
                    if (linkList.size() > 2) {
                        String downloadLink = linkList.get(2).getAttribute("href");
                        article.downloadLink = downloadLink;
                    }
                    String pmcId = webElement.findElement(By.cssSelector(".rprtid")).getText();
                    article.pmcId = pmcId.split(":")[1].trim().substring(3);
                    articleList.add(article);
                } catch (Exception exc) {
                    System.err.println("Error while processing title: " + exc.getMessage());
                }
            }
        }catch (Exception exc){
            System.err.println("Failed parsing " + pageNumber + " page.");
            System.err.println(exc.getMessage());
            exc.printStackTrace();
        }
        return articleList;
    }
}
