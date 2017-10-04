import java.util.LinkedList;
import java.util.List;

/**
 * Created by zhuoli on 6/24/17.
 */
public class Article {
    public String title ="";
    public String[] authors = new String[]{""};
    public String pmcId = "";
    public List<String> citationList = new LinkedList<>();
    public List<String> references = new LinkedList<>();
    public String keyWord = "";
    public String url = "";
    public String downloadLink = "";
    public String datePublished = "";


    private static String CSV_SEPERATOR = ",";
    private static String IN_VALUE_SEPERATOR = ";";

    public static String getHeaders(){
        return "Title" + Article.CSV_SEPERATOR +
                "Authors" + Article.CSV_SEPERATOR +
                "PmcId" + Article.CSV_SEPERATOR +
                "Citations" + Article.CSV_SEPERATOR +
                "Keywords" + Article.CSV_SEPERATOR +
                "Url" + Article.CSV_SEPERATOR +
                "Publish Date" + Article.CSV_SEPERATOR +
                "Reference";
    }

    @Override
    public String toString(){
        return this.title.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.getAuthors().replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.pmcId.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.getCitations().replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.keyWord.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.url.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR)  + Article.CSV_SEPERATOR +
                this.datePublished.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.getReference().replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR);
    }

    public String getAuthors(){
        String sb = "";
        for(String author :authors){
            sb = sb + IN_VALUE_SEPERATOR + author;
        }
        return sb.length()>1 ? sb.substring(1) : "";
    }

    public String getCitations(){
        String sb = "";
        for(String cit : citationList){
            sb = sb +IN_VALUE_SEPERATOR + cit;
        }
        return sb.length()>1 ? sb.substring(1) : "";
    }

    public String getReference(){
        String sb = "";
        for(String cit : references){
            sb = sb +IN_VALUE_SEPERATOR + cit;
        }
        return sb.length()>1 ? sb.substring(1) : "";
    }
}
