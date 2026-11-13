package proto.server;

import org.junit.jupiter.api.Test;
import proto.entity.Category;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CategoryJsonMapperTest {

    @Test
    void roundTripsIdAndNameIncludingCommaAndQuotes() {
        Category club = new Category("club", "동아리, \"모임\"");
        Category personal = new Category(Category.PERSONAL_ID, "개인");

        List<Category> back = CategoryJsonMapper.readSnapshot(
                CategoryJsonMapper.writeSnapshot(List.of(club, personal)));

        assertEquals(2, back.size());
        assertEquals("club", back.get(0).getId());
        assertEquals("동아리, \"모임\"", back.get(0).getName());
        assertEquals(Category.PERSONAL_ID, back.get(1).getId());
        assertEquals("개인", back.get(1).getName());
    }
}
