import java.util.List;

/**
 * Created by zhuoli on 6/24/17.
 */
public class Article {
    public String title;
    public String[] authors;
    public String pmcId;
    public List<String> citationList;
    public String url;
    public String downloadLink;

    private static String CSV_SEPERATOR = ",";
    private static String IN_VALUE_SEPERATOR = ";";

    public static String getHeaders(){
        return "Title" + Article.CSV_SEPERATOR + "Authors" + Article.CSV_SEPERATOR + "PmcId" + Article.CSV_SEPERATOR + "Citations" + Article.CSV_SEPERATOR + "Url";
    }

    @Override
    public String toString(){
        return this.title.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.getAuthors() + Article.CSV_SEPERATOR +
                this.pmcId.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR) + Article.CSV_SEPERATOR +
                this.getCitations() + Article.CSV_SEPERATOR +
                this.url.replace(CSV_SEPERATOR, IN_VALUE_SEPERATOR);
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
}
