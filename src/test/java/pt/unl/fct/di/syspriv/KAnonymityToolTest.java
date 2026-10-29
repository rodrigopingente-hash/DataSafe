
package pt.unl.fct.di.syspriv;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

import pt.unl.fct.di.syspriv.entities.AnonymousHealthData;
import pt.unl.fct.di.syspriv.kanonymity.KAnonymityTool;

public class KAnonymityToolTest {

    @Test
    void testKAnonymity() {

        List<AnonymousHealthData> records =
                new ArrayList<>();

        Date date = new Date();

        records.add(new AnonymousHealthData(
                1L, 2000, 1000, 90, date));

        records.add(new AnonymousHealthData(
                2L, 2000, 1000, 120, date));

        records.add(new AnonymousHealthData(
                3L, 1995, 2000, 100, date));

        records.add(new AnonymousHealthData(
                4L, 1995, 2000, 150, date));

        KAnonymityTool<AnonymousHealthData> tool =
                new KAnonymityTool<>(2, 0.2f);

        assertTrue(tool.isKAnonymous(records));
    }


@Test
void testMondrian() {

    List<AnonymousHealthData> records =
            new ArrayList<>();

    Date date = new Date();

    for (int i = 0; i < 20; i++) {

        records.add(new AnonymousHealthData(
                (long) (i + 1),
                1980 + i,
                1000 + i * 10,
                80 + i * 5,
                date
        ));
    }

    KAnonymityTool<AnonymousHealthData> tool =
            new KAnonymityTool<>(3, 0.2f);

    List<AnonymousHealthData> anonymized =
            tool.runMondrain(records);

    assertEquals(records.size(), anonymized.size());

    assertTrue(tool.isKAnonymous(anonymized));

    for (AnonymousHealthData record : anonymized) {
        assertNull(record.getId());
    }
}


@Test
void testTCloseMondrian() {

    List<AnonymousHealthData> records =
            new ArrayList<>();

    Date date = new Date();

    for (int i = 0; i < 20; i++) {
        records.add(new AnonymousHealthData(
                (long) (i + 1),
                1980 + i,
                1000 + i * 10,
                80 + (i % 5) * 10,
                date
        ));
    }

    KAnonymityTool<AnonymousHealthData> tool =
            new KAnonymityTool<>(3, 0.2f);

    List<AnonymousHealthData> anonymized =
            tool.runTCloseMondrain(records);

    assertEquals(records.size(), anonymized.size());
    assertTrue(tool.isKAnonymous(anonymized));

    // Verify t-closeness for every generalized group.
    Map<Map<String, Object>, List<AnonymousHealthData>> groups =
            new HashMap<>();

    for (AnonymousHealthData record : anonymized) {
        Map<String, Object> qi =
                new HashMap<>(record.getQuasiIdentifiers());

        groups.computeIfAbsent(
                qi, key -> new ArrayList<>()
        ).add(record);
    }

    for (List<AnonymousHealthData> group : groups.values()) {
        assertTrue(tool.isTClose(group, anonymized));
    }
}

}
