package com;

import com.VariablesClass;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.cdimascio.dotenv.Dotenv;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.HashSet;
import java.nio.file.Files;








public class CompanyActivities extends AbstractClass {
    private static final OkHttpClient client = new OkHttpClient();
    private static final ObjectMapper mapper = new ObjectMapper();

    public void findCompaniesWithPermanentlyClosureNotes(String token) throws IOException {
        VariablesClass variablesClass = new VariablesClass();
        Set<String> excludedCompanyIds = variablesClass.getExcludedCompanyIds();
        boolean hasMoreCompanies = true;
        String after = null;
        int companyCount = 0;
        int companyLimit = 100; // change the number of companies
        int companyOrderNum = 0;
        int matchCount = 0;
        String env = System.getenv("GITHUB_ENVIRONMENT");
        if (env == null || env.isEmpty()) {
            env = "local";
        }
        String filename = "permanently_closed_notes_" + env + ".csv";
        Path outputPath = Paths.get(filename);
        PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath));
        writer.println(String.join(",", escapeCsv(new String[]{"Company ID", "Company Name", "Note ID", "Created Date", "Note Body"})));
        writer.flush();

        while (hasMoreCompanies && companyCount < companyLimit) {
            HttpUrl.Builder urlBuilder = HttpUrl.parse(BASE_URL + "/crm/v3/objects/companies")
                    .newBuilder()
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("properties", "name");

            if (after != null) {
                urlBuilder.addQueryParameter("after", after);
            }

            Request request = new Request.Builder()
                    .url(urlBuilder.build())
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Failed to fetch companies: " + response.code());
                    return;
                }

                String json = response.body().string();
                JsonNode root = mapper.readTree(json);

                for (JsonNode company : root.path("results")) {
                    companyOrderNum++;
                    if (companyCount >= companyLimit) break;

                    String companyId = company.path("id").asText("");
                    if (excludedCompanyIds.contains(companyId)) {
                        System.out.println("Skipping excluded company ID: " + companyId);
                        continue;
                    }
                    String companyName = company.path("properties").path("name").asText("");
                    companyCount++;

                    boolean hasMoreNotes = true;
                    long offset = 0;

                    while (hasMoreNotes) {

                        HttpUrl notesUrl = HttpUrl.parse("https://api.hubapi.com/engagements/v1/engagements/associated/company/" + companyId + "/paged")
                                .newBuilder()
                                .addQueryParameter("limit", "100")
                                .addQueryParameter("offset", String.valueOf(offset))
                                .build();

                        Request notesRequest = new Request.Builder()
                                .url(notesUrl)
                                .addHeader("Authorization", "Bearer " + token)
                                .addHeader("Content-Type", "application/json")
                                .build();

                        try (Response notesResponse = client.newCall(notesRequest).execute()) {
                            if (!notesResponse.isSuccessful()) {
                                System.err.println("Failed to fetch notes for company " + companyId + ": " + notesResponse.code());
                                break;
                            }

                            String notesJson = notesResponse.body().string();
                            JsonNode notesRoot = mapper.readTree(notesJson);

                            for (JsonNode engagementNode : notesRoot.path("results")) {
                                JsonNode engagement = engagementNode.path("engagement");
                                String type = engagement.path("type").asText("");
                                if ("NOTE".equalsIgnoreCase(type)) {
                                    String bodyRaw = engagementNode.path("metadata").path("body").asText("");
                                    String bodyLower = bodyRaw.toLowerCase();
                                    String noteId = engagement.path("id").asText("");
                                    System.out.println(companyOrderNum + ". company " + companyId + " note ID " + noteId);
                                    if (bodyLower.contains("permanently closed")) {
                                        String created = formatDate(engagement.path("timestamp").asText(""));
                                        matchCount++;
                                        writer.println(String.join(",", escapeCsv(new String[]{companyId, companyName, noteId, created, bodyRaw})));
                                        writer.flush();
                                    }
                                }
                            }

                            hasMoreNotes = notesRoot.path("hasMore").asBoolean(false);
                            offset = notesRoot.path("offset").asLong(0);
                        }
                    }
                }

                hasMoreCompanies = root.has("paging") && root.path("paging").has("next");
                if (hasMoreCompanies) {
                    after = root.path("paging").path("next").path("after").asText();
                }
            }
        }

        System.out.println("Companies with 'permanently closed' notes found: " + matchCount);

        writer.close();
        System.out.println("Results saved to: " + outputPath.toAbsolutePath());

    }


}
