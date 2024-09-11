package org.example;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.ArrayList;

public class Main {

    private static final String URL = "jdbc:postgresql://localhost:5432/forumPosts";
    private static final String USER = "postgres";
    private static final String PASSWORD = "3567";

    public static void main(String[] args) {
        try {
            // Conectar ao banco de dados
            Connection connection = DriverManager.getConnection(URL, USER, PASSWORD);
            System.out.println("Conexão bem-sucedida!");

            // URL base do fórum para listagem de tópicos
            String baseForumUrl = "https://forum.lolesporte.com/viewforum.php?f=10";

            // Obter URLs dos tópicos e processar páginas de listagem de tópicos
            processTopicList(connection, baseForumUrl);

            // Fechar a conexão
            connection.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private static void processTopicList(Connection connection, String baseForumUrl) throws Exception {
        String currentPageUrl = baseForumUrl;
        Set<String> visitedPages = new HashSet<>();

        while (currentPageUrl != null && !visitedPages.contains(currentPageUrl)) {
            visitedPages.add(currentPageUrl);
            Document forumDoc = Jsoup.connect(currentPageUrl).get();

            // Obter URLs dos tópicos da página atual
            List<String> topicUrls = getTopicUrls(forumDoc);

            for (String topicUrl : topicUrls) {
                // Processar cada tópico
                processTopic(connection, topicUrl);
            }

            // Obter o link para a próxima página de tópicos
            currentPageUrl = getNextTopicPageUrl(forumDoc);
        }
    }

    private static List<String> getTopicUrls(Document forumDoc) {
        List<String> topicUrls = new ArrayList<>();

        // Encontrar todos os links dos tópicos
        Elements topicLinks = forumDoc.select("a.topictitle");

        for (Element link : topicLinks) {
            String href = link.attr("href");
            // Construir URL completa do tópico
            String topicUrl = "https://forum.lolesporte.com/" + href;
            topicUrls.add(topicUrl);
        }

        return topicUrls;
    }

    private static String getNextTopicPageUrl(Document doc) {
        Element nextPageLink = doc.select("a[rel=next]").first();
        if (nextPageLink != null) {
            return "https://forum.lolesporte.com/" + nextPageLink.attr("href");
        }
        return null;
    }

    private static void processTopic(Connection connection, String topicUrl) throws Exception {
        String currentPageUrl = topicUrl;
        Set<String> visitedPages = new HashSet<>();

        while (currentPageUrl != null && !visitedPages.contains(currentPageUrl)) {
            visitedPages.add(currentPageUrl);
            Document doc = Jsoup.connect(currentPageUrl).get();

            // Encontrar todos os artigos (posts)
            Elements articles = doc.select("article");

            // Obter o título do tópico
            String topicTitle = doc.selectFirst("title").text();
            String topicLink = topicUrl;

            // Inserir o tópico no banco de dados
            int topicId = insertTopicIntoDatabase(connection, topicTitle, topicLink);

            // Verificar se encontrou posts
            if (articles.isEmpty()) {
                System.out.println("Nenhum post encontrado no tópico: " + topicTitle);
            } else {
                int postCount = 0;

                // Processar cada artigo (post)
                for (Element article : articles) {
                    // Encontrar o conteúdo do post
                    Element contentDiv = article.selectFirst("div.content");
                    String postContent = contentDiv != null ? contentDiv.text() : "";

                    // Encontrar a data do post
                    String postDate = article.selectFirst(".post-id a") != null ? article.selectFirst(".post-id a").text() : "";

                    // Encontrar o autor do post
                    Element authorElement = article.selectFirst("div.avatar-over font b"); // Seleciona o autor
                    String postAuthor = authorElement != null ? authorElement.text() : "";

                    // Inserir o post no banco de dados
                    insertPostIntoDatabase(connection, topicId, postContent, postDate, postAuthor);
                    postCount++;
                }

                // Mostrar a contagem de posts encontrados
                System.out.println("Tópico: " + topicTitle + " - Posts encontrados: " + postCount);
            }

            // Obter o link para a próxima página de posts
            currentPageUrl = getNextPageUrl(doc);
        }
    }

    private static String getNextPageUrl(Document doc) {
        Element nextPageLink = doc.select("a[rel=next]").first();
        if (nextPageLink != null) {
            return "https://forum.lolesporte.com/" + nextPageLink.attr("href");
        }
        return null;
    }

    private static int insertTopicIntoDatabase(Connection connection, String title, String link) throws SQLException {
        String insertTopicSQL = "INSERT INTO topics (title, link) VALUES (?, ?) RETURNING id";
        try (PreparedStatement pstmt = connection.prepareStatement(insertTopicSQL)) {
            pstmt.setString(1, title);
            pstmt.setString(2, link);
            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt(1); // Obtém o ID retornado
                } else {
                    throw new SQLException("Failed to obtain topic ID.");
                }
            }
        }
    }

    private static void insertPostIntoDatabase(Connection connection, int topicId, String content, String date, String author) throws SQLException {
        String insertPostSQL = "INSERT INTO posts (topic_id, content, date, author) VALUES (?, ?, ?, ?)";
        try (PreparedStatement pstmt = connection.prepareStatement(insertPostSQL)) {
            pstmt.setInt(1, topicId);
            pstmt.setString(2, content);

            // Converte a data para Timestamp
            Timestamp timestamp = convertStringToTimestamp(date);
            pstmt.setTimestamp(3, timestamp);

            pstmt.setString(4, author);
            pstmt.executeUpdate();
        }
    }

    private static Timestamp convertStringToTimestamp(String dateStr) {
        // Formato da data no HTML
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd/MM/yyyy, HH:mm"); // Ajuste o formato conforme necessário
        try {
            Date parsedDate = dateFormat.parse(dateStr);
            return new Timestamp(parsedDate.getTime());
        } catch (ParseException e) {
            e.printStackTrace();
            return null; // Retorne null ou uma data padrão em caso de erro
        }
    }
}
