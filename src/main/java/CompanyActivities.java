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

    public void fetchCompanies(String token) throws IOException {
        HttpUrl url = HttpUrl.parse(BASE_URL + "/crm/v3/objects/companies")
                .newBuilder()
                .addQueryParameter("limit", "100")
                .addQueryParameter("properties", "name,domain,industry,createdate")
                .addQueryParameter("archived", "false")
                .build();

        Request request = new Request.Builder()
                .url(url)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                System.err.println("Request failed: " + response.code());
                System.err.println("Response body: " + response.body().string());
                return;
            }

            String json = response.body().string();
            JsonNode root = mapper.readTree(json);
            List<String[]> rows = new ArrayList<>();
            rows.add(new String[]{"Name", "Domain", "Industry", "Created Date"});

            for (JsonNode company : root.path("results")) {
                ObjectNode props = (ObjectNode) company.path("properties");
                String name = props.path("name").asText("");
                String domain = props.path("domain").asText("");
                String industry = props.path("industry").asText("");
                String created = props.path("createdate").asText("");
                rows.add(new String[]{name, domain, industry, created});
            }

            try (PrintWriter writer = new PrintWriter(Paths.get("companies.csv").toFile())) {
                for (String[] row : rows) {
                    writer.println(String.join(",", escapeCsv(row)));
                }
                System.out.println("Exported " + (rows.size() - 1) + " companies to companies.csv");
            }
        }
    }

    public void getActivities(String token) throws IOException {
        String companyId = "6326336895";
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"ID", "Type", "Created Date", "Subject", "Body"});

        boolean hasMore = true;
        long offset = 0;

        while (hasMore) {
            HttpUrl url = HttpUrl.parse("https://api.hubapi.com/engagements/v1/engagements/associated/company/" + companyId + "/paged")
                    .newBuilder()
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("offset", String.valueOf(offset))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Request failed: " + response.code());
                    return;
                }

                String json = response.body().string();
                JsonNode root = mapper.readTree(json);

                for (JsonNode engagementNode : root.path("results")) {
                    JsonNode engagement = engagementNode.path("engagement");
                    String type = engagement.path("type").asText("");
                    if ("CALL".equalsIgnoreCase(type)) {
                        String id = engagement.path("id").asText("");
                        String created = formatDate(engagement.path("timestamp").asText(""));

                        String subject = engagementNode.path("metadata").path("subject").asText("");
                        if (subject.isEmpty()) {
                            subject = engagementNode.path("metadata").path("body").asText("");
                        }

                        String body = engagementNode.path("metadata").path("body").asText("");
                        rows.add(new String[]{id, type, created, subject, body});
                    }
                }

                hasMore = root.path("hasMore").asBoolean(false);
                offset = root.path("offset").asLong(0);
            }
        }

        System.out.println("Total calls found: " + (rows.size() - 1));

        Path outputPath = Paths.get("calls.csv");
        try (PrintWriter writer = new PrintWriter(outputPath.toFile())) {
            for (String[] row : rows) {
                writer.println(String.join(",", escapeCsv(row)));
            }
            System.out.println("Filtered calls saved to: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write output file: " + e.getMessage());
        }
    }

    public void getNotes(String token) throws IOException {
        String companyId = "6326336895";
        List<String[]> rows = new ArrayList<>();
        rows.add(new String[]{"Company ID", "Company Name", "Note ID", "Created Date", "Body"});


        boolean hasMore = true;
        long offset = 0;

        String companyName = "";

        HttpUrl companyUrl = HttpUrl.parse(BASE_URL + "/crm/v3/objects/companies/" + companyId)
                .newBuilder()
                .addQueryParameter("properties", "name")
                .build();

        Request companyRequest = new Request.Builder()
                .url(companyUrl)
                .addHeader("Authorization", "Bearer " + token)
                .addHeader("Content-Type", "application/json")
                .build();

        try (Response companyResponse = client.newCall(companyRequest).execute()) {
            if (companyResponse.isSuccessful()) {
                String companyJson = companyResponse.body().string();
                JsonNode companyRoot = mapper.readTree(companyJson);
                companyName = companyRoot.path("properties").path("name").asText("");
            } else {
                System.err.println("Failed to fetch company name: " + companyResponse.code());
            }
        }


        while (hasMore) {
            HttpUrl url = HttpUrl.parse("https://api.hubapi.com/engagements/v1/engagements/associated/company/" + companyId + "/paged")
                    .newBuilder()
                    .addQueryParameter("limit", "100")
                    .addQueryParameter("offset", String.valueOf(offset))
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .addHeader("Authorization", "Bearer " + token)
                    .addHeader("Content-Type", "application/json")
                    .build();

            try (Response response = client.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    System.err.println("Request failed: " + response.code());
                    return;
                }

                String json = response.body().string();
                JsonNode root = mapper.readTree(json);

                for (JsonNode engagementNode : root.path("results")) {
                    JsonNode engagement = engagementNode.path("engagement");
                    String type = engagement.path("type").asText("");
                    if ("NOTE".equalsIgnoreCase(type)) {
                        String id = engagement.path("id").asText("");
                        String created = formatDate(engagement.path("timestamp").asText(""));
                        String body = engagementNode.path("metadata").path("body").asText("");
                        rows.add(new String[]{companyId, companyName, id, created, body});

                    }
                }

                hasMore = root.path("hasMore").asBoolean(false);
                offset = root.path("offset").asLong(0);
            }
        }

        System.out.println("Total notes found: " + (rows.size() - 1));

        rows.subList(1, rows.size()).sort((a, b) -> b[1].compareTo(a[1]));
        Path outputPath = Paths.get("notes.csv");
        try (PrintWriter writer = new PrintWriter(outputPath.toFile())) {
            for (String[] row : rows) {
                writer.println(String.join(",", escapeCsv(row)));
            }
            System.out.println("Notes saved to: " + outputPath.toAbsolutePath());
        } catch (IOException e) {
            System.err.println("Failed to write notes file: " + e.getMessage());
        }
    }

    public void findCompaniesWithPermanentlyClosureNotes(String token) throws IOException {
        boolean hasMoreCompanies = true;
        String after = null;
        int companyCount = 0;
        int companyLimit = 10; // change the number of companies
        int companyOrderNum = 0;
        int matchCount = 0;
        Path outputPath = Paths.get("permanently_closed_notes.csv");
        PrintWriter writer = new PrintWriter(Files.newBufferedWriter(outputPath));
        writer.println(String.join(",", escapeCsv(new String[]{"Company ID", "Company Name", "Note ID", "Created Date", "Note Body"})));
        writer.flush();
        Set<String> excludedCompanyIds = new HashSet<>(List.of(
                "20594846040","20593460011","20474812721","20149507496","19731775305","19643851570","19612497444",
                "19413809544","19412993601","19410094196","19398035783","19396942101","19396371086","19396391259",
                "19396024837","19303222082","19149163410","19030569504","18996696141","18962147604","18961131421",
                "18960966944","18838277924","18829796181","18723028295","18694677325","18682756231","18680974003",
                "18674530460","18566256791","18536679574","18491111722","18471730261","18439042741","18435809958",
                "18427498114","18406904502","18342779678","18320394314","18263846327","18200093220","18199937906",
                "17909768029","17909021473","17785430687","17731121557","17665056856","17661625724","17565464236",
                "17253801135","17206364826","17184242502","17184130488","17147929696","17146900118","17134330189",
                "16961446424","16959517559","16921252116","16920155316","16794531256","16789675043","16789826991",
                "16776504618","16774000559","16269944231","16268753795","16268830765","16257875211","15773406230",
                "15772650139","15715689313","15601761086","15590639536","15590579208","15525652584","15463957916",
                "15378886323","15316119419","12124958022","11578233195","10425499687","10324413794","10028296331",
                "10027844228","10027902602","9678463261","9523298642","9345175435","9343798325","9164800277",
                "9137528959","8920773214","8816927033","8816418214","8788746318","8781609750","8781668965",
                "8781609388","8779812673","8781547100","7915989098","7727584525","7185475421","6143869599",
                "5745600423","4461418207","4457881625","4412033386","4410772800","4224925803","3886945608",
                "2578781003","34182997910","1128382462","667850708","615885497","494092136","428892180",
                "416564402","314682418","232859071","307205998","246958239","246269119","262135508","215303227",
                "289687250","212999152","168987834","286843610","39515328534","39357684880","39294037941",
                "39272168980","39278754867","39123806780","38691950446","38580700969","37215684696","37205560593",
                "35155400309","33621279616","33584229940","31988125056","31470997942","31455422482","31407788320",
                "31410929568","31383431817","31368280382","31200815505","31129361017","30944948007","30948622752",
                "30676333621","30510328144","30093678864","29939101100","29644994986","29638248279","29340380504",
                "28844062503","28275530363","27852741992","27591427616","27367452010","26878451850","26505160776",
                "26505961629","26409708370","26114832270","26113471012","26113470868","25982431141","25973523534",
                "25976370550","23615715961","23564303635","23103478393","22835898673","22404707710","22324858760",
                "22284531883","22264205626","22137835828","21902921528","21834428272","21682687339","21643392411",
                "21642466320","21641574994","21630027915","21610736274","21609532698","21403260591","21290897920",
                "21286200606","21133291444","20981529618","34329156886","17769628600","17768998673","7981580687",
                "7933031473","3827954905","29863185438","29821277329","200077861","271729663","271716018",
                "10824870492"
        ));



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
