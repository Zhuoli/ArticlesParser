import org.apache.commons.cli.*;
import org.apache.commons.io.FileUtils;
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
    protected static String BASE_URL = "https://www.ncbi.nlm.nih.gov/pubmed";
    private static Optional<CommandLine> cmd = Optional.empty();
    private static List<String> failedIds = new LinkedList<>();
    public static void main(String[] args){
        RunMe.cmd = RunMe.parseArguments(args);
        System.out.println("Input: " + Arrays.toString(args));
        String keyword;

        // Read keyword from console if not set in arguments
        if (!cmd.isPresent() || !cmd.get().hasOption(CommandConstant.KEY_WORD) || cmd.get().getOptionValue(CommandConstant.KEY_WORD).length()<1) {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                System.out.print("Keyword is not set in arguments, please type keyword to parse: \n");

                keyword= reader.readLine();
                System.out.println("Your keyword is: " + keyword);
            } catch (IOException ioe) {
                ioe.printStackTrace();
                keyword = "";
                System.exit(1);
            }
        }else {
             keyword = cmd.get().getOptionValue(CommandConstant.KEY_WORD);
        }

        System.out.println("Website to parse: " + BASE_URL);

        Optional<Integer> maxPageNum = Optional.empty();

        if (cmd.isPresent() && cmd.get().hasOption(CommandConstant.PAGE_NUM)){
            String numStr = cmd.get().getOptionValue(CommandConstant.PAGE_NUM);
            try {
                maxPageNum = Optional.of(Integer.parseInt(numStr));
            }catch (NumberFormatException exc){
                System.err.println("Incorrect max page number.");
            }
        }

        new RunMe().parsePage(BASE_URL, keyword, maxPageNum);
    }

    private static Optional<CommandLine> parseArguments(String[] args){

        // create Options object
        Options options = new Options();

        // add t option
        options.addOption(CommandConstant.KEY_WORD, true, "Key word for searching.");
        options.addOption(CommandConstant.PAGE_NUM, true, "Maximum number of pages to parse per query");
        options.addOption(CommandConstant.DEBUG, false, "Is in debug model or jar model");
        options.addOption(CommandConstant.DISABLE_ABSTRACTS,false, "Disable parsing abstracts and keywords to accelerate the speed.");
        options.addOption(CommandConstant.SKIP_TP, true, "Starting page of the parsing result.");
        CommandLineParser parser = new DefaultParser();
        try {
            return Optional.of(parser.parse(options, args));
        } catch (ParseException exc) {
            System.err.println("Arguments parse exception: " + exc.getMessage());
            return Optional.empty();
        }
    }

    public void parsePage(String url, String keyword, Optional<Integer> maxPageNumOptional){

        String fileName = keyword.replace("/", "").replace("\\", "");
        if (fileName.length()>100){
            fileName = fileName.substring(0, 100) + "...";
        }
        String cititationFolderName = String.format("abstracts_%s", fileName);
        cititationFolderName = cititationFolderName.substring(0, Math.min(cititationFolderName.length(), 255));
        fileName = fileName.substring(0, Math.min(fileName.length(), 255)) + ".csv";

        // Clean up previous file and add csv header
        this.writeToFile(fileName, Article.getHeaders() + System.lineSeparator(), false);

        if (cmd.isPresent() && cmd.get().hasOption(CommandConstant.DEBUG)) {
            System.setProperty("webdriver.gecko.driver", "./src/main/resources/geckodriver");
        }else{
            System.setProperty("webdriver.gecko.driver", "./geckodriver");
        }

        WebDriver driver = new FirefoxDriver();
        driver.get(url);
        WebElement keyWordInputElement = driver.findElement(By.cssSelector("input[id='term']"));
        keyWordInputElement.sendKeys(keyword);
        WebElement searchButton = driver.findElement(By.cssSelector("button[id='search']"));
        searchButton.click();

        if(cmd.isPresent() && cmd.get().hasOption(CommandConstant.SKIP_TP)) {

            String skipToNum = cmd.get().getOptionValue(CommandConstant.SKIP_TP);
            WebElement pageNumberInputElement = driver.findElement(By.cssSelector("input[id='pageno']"));
            pageNumberInputElement.clear();
            pageNumberInputElement.sendKeys(skipToNum);
            pageNumberInputElement.sendKeys(Keys.RETURN);
        }

        int pageCount = 0;
        while(true) {
            pageCount++;
            if (maxPageNumOptional.isPresent() && pageCount > maxPageNumOptional.get()){
                break;
            }

            List<Article> articleList = this.retrieveArticleInstance(driver, pageCount);
            for (Article article : articleList) {
                try {
                    String citationXml = this.retrieveCitationId(article.pmcId);
                    citationXml = citationXml.substring(citationXml.indexOf("<eLinkResult>"));
                    article.citationList = this.retrieveLinkIdsFromCsv(citationXml);
                    String referenceXml = this.retrieveReference(article.pmcId);
                    referenceXml = referenceXml.substring(citationXml.indexOf("<eLinkResult>"));
                    article.references = this.retrieveLinkIdsFromCsv(referenceXml);

                    if (!RunMe.cmd.isPresent() || !RunMe.cmd.get().hasOption(CommandConstant.DISABLE_ABSTRACTS)) {
                        String[] keywordsAndAbstracts = this.retrieveKeyWordsAndAbstract(article.url);
                        article.keyWord = keywordsAndAbstracts[0];
                        this.writeToFile(String.format("./%s/abstract_%s.txt", cititationFolderName, article.pmcId), keywordsAndAbstracts[1], false);
                    }
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
            this.writeToFile(fileName, sb.toString(), true);


            List<WebElement> nextPageClicks = driver.findElements(By.cssSelector("a[title='Next page of results']"));
            if(nextPageClicks.size()==0){
                break;
            }
            nextPageClicks.get(0).click();
        }

        driver.close();
        if(RunMe.failedIds.size()!=0) {
            System.out.println(RunMe.failedIds.size() + " papers are failed to parse, them are:");
            for(String title: RunMe.failedIds){
                System.out.println(title);
            }
        }
        System.out.println("\nYOU ARE ALL SET \nParsing result saved at '" + fileName + "' ENJOY!");
    }

    private boolean writeToFile(String fileName, String content, boolean isAppend){
        File pathToFile = new File(fileName);
        try {
            FileUtils.write(pathToFile, content, "utf-8", isAppend);
        }catch (IOException exc){
            System.err.println("Failed to create file directories: " + fileName + "  " + exc.getMessage());
            System.out.println("\nPress any key to exit.");
            try {
                System.in.read();
            }catch (Exception e){
            }
            System.exit(-1);
        }
        return true;
    }

    private List<String> retrieveLinkIdsFromCsv(String csvContent) throws ParserConfigurationException, IOException, SAXException {
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
        List<WebElement> abstractElements = null;
        WebElement keywordsElement = null;
        try {
            // Navigate to Google
            driver.get(url);
            try {
                keywordsElement = driver.findElement(By.cssSelector("div[class='keywords']  p"));
                keywords = keywordsElement==null? keywords : keywordsElement.getText();
            }catch (NoSuchElementException exc){
                System.err.println("Article keywords doesn't exist at : " + url);
                System.err.println(exc.getMessage());
            }
            try{
                abstractElements = driver.findElements(By.cssSelector("abstracttext"));
                abstracts = abstractElements.stream().map(c -> c.getText()).reduce((a,b) -> a+"\t\n"+b).get();
            }catch (NoSuchElementException exc){
                System.err.println("Article abstracts doesn't exist at : " + url);
                System.err.println(exc.getMessage());

            }
        }catch (Exception exc)
        {
            System.err.println("Article keyword doesn't exist at : " + url);
            System.err.println(exc.getMessage());

        }finally {
            driver.close();
        }

        return new String[]{keywords, abstracts};
    }

    static String citationBaseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/elink.fcgi?dbfrom=pubmed&linkname=pubmed_pubmed_citedin&id=";
    private String retrieveCitationId(String citationId) throws IOException{
        return this.retrieveXMLUrl(citationBaseUrl+citationId);
    }

    static String referenceBaseUrl = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/elink.fcgi?dbfrom=pubmed&linkname=pubmed_pubmed_refs&id=";
    private String retrieveReference(String citationId) throws IOException{
        return this.retrieveXMLUrl(referenceBaseUrl+citationId);
    }

    private String retrieveXMLUrl(String url) throws IOException{
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
                String title = null;
                try {
                    Article article = new Article();
                    title = webElement.findElement(By.cssSelector(".title")).getText();
                    String href = webElement.findElement(By.cssSelector(".title")).findElement(By.cssSelector("a")).getAttribute("href");
                    String authors = webElement.findElement(By.cssSelector(".desc")).getText();
                    String details = webElement.findElement(By.cssSelector(".details")).getText();
                    article.title = title;
                    article.url = href;
                    article.authors = authors.split(",");
                    article.datePublished = details.split("\\.")[1].split(";")[0].split(":")[0];

                    WebElement linkBlock = webElement.findElement(By.cssSelector(".links"));
                    List<WebElement> linkList = linkBlock.findElements(By.cssSelector("a"));
                    if (linkList.size() > 2) {
                        String downloadLink = linkList.get(2).getAttribute("href");
                        article.downloadLink = downloadLink;
                    }
                    String pmcId = webElement.findElement(By.cssSelector(".rprtid")).getText();
                    article.pmcId = pmcId.split(":")[1].trim();
                    articleList.add(article);
                } catch (Exception exc) {
                    RunMe.failedIds.add(title!=null? title : "Failuer at Page Number: "+pageNumber + exc.getMessage());
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
