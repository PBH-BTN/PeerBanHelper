import java.util.regex.Pattern;

public class Main {
    public static void main(String[] args) {
        Pattern pattern = Pattern.compile(".*/static/*");
        System.out.println(pattern.matcher("src/main/java/resources/static/index.html").find());
        System.out.println(pattern.matcher("src/main/java/resources/static/css/main.css").find());
    }
}
