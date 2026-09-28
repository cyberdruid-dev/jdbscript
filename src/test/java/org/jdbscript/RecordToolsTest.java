package org.jdbscript;

import org.jdbscript.impl.JDBRecord;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class RecordToolsTest {

    private RecordTools tools;
    private JDBRecord record;

    @BeforeMethod
    public void setUp() throws Exception {
        this.tools = new RecordTools();
        this.record = new JDBRecord("the_table_name");
        this.tools.setRecord(this.record);
    }

    @Test
    public void nextInt_should_return_first_value_on_first_call(){
        final int firstValue = 457;

        int result = tools.nextIntId("name?", firstValue);

        assertThat(result).describedAs("nextInt()")
                .isEqualTo(firstValue);
    }

    @Test
    public void nextInt_should_increment_result_by_one_on_each_successive_call(){
        final int firstValue = 457;

        int result = tools.nextIntId("name?", firstValue);
        int result2 = tools.nextIntId("name?", firstValue);
        int result3 = tools.nextIntId("name?", firstValue);

        assertThat(result2).describedAs("second call to nextInt()")
                .isEqualTo(firstValue+1);
        assertThat(result3).describedAs("third call to nextInt()")
                .isEqualTo(firstValue+2);
    }

    @Test
    public void nextLong_should_return_first_value_on_first_call(){
        final long firstValue = 457;

        long result = tools.nextLongId("name?", firstValue);

        assertThat(result).describedAs("nextLong()")
                .isEqualTo(firstValue);
    }


    @Test
    public void nextLong_should_increment_result_by_one_on_each_successive_call(){
        final long firstValue = 457;

        long result = tools.nextLongId("name?", firstValue);
        long result2 = tools.nextLongId("name?", firstValue);
        long result3 = tools.nextLongId("name?", firstValue);

        assertThat(result2).describedAs("second call to nextLong()")
                .isEqualTo(firstValue+1);
        assertThat(result3).describedAs("third call to nextLong()")
                .isEqualTo(firstValue+2);
    }

    @Test
    public void strValue_should_template_if_template_does_not_contain_variable_pattern(){
        String template = "just a string";

        String result = tools.strValue(template);

        assertThat(result).describedAs("strValue('%s')", template)
                .isEqualTo(template);
    }

    @Test
    public void strValue_should_replace_variable_pattern_with_value_from_record(){
        String template = "some template ${some_key}!";
        this.record.setColumnValue("some_key", "some_value");
        String expectedResult = "some template some_value!";

        String result = tools.strValue(template);

        assertThat(result).describedAs("strValue('%s')", template)
                .isEqualTo(expectedResult);
    }

    @Test
    public void strValue_should_replace_multiple_variable_patterns(){
        String template = "some template ${some_key}! ${another_key}.";
        this.record.setColumnValue("some_key", "some value");
        this.record.setColumnValue("another_key", "another value");
        String expectedResult = "some template some value! another value.";

        String result = tools.strValue(template);

        assertThat(result).describedAs("strValue('%s')", template)
                .isEqualTo(expectedResult);
    }
}
