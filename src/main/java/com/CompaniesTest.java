package com;


import java.io.IOException;
import com.VariablesClass;

public class CompaniesTest extends AbstractClass {
    public static void main(String[] args) throws IOException {
        String token = new CompaniesTest().getAccessToken();
        CompanyActivities runner = new CompanyActivities();
        runner.findCompaniesWithPermanentlyClosureNotes(token);
        runner.shutdown();
    }
}
