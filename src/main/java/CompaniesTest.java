import java.io.IOException;

public class CompaniesTest extends AbstractClass {
    public static void main(String[] args) throws IOException {
        String token = new CompaniesTest().getAccessToken();
        CompanyActivities runner = new CompanyActivities();

        //runner.fetchCompanies(token);
        //runner.getActivities(token);
        runner.findCompaniesWithPermanentlyClosureNotes(token);
        runner.shutdown();
    }
}
