package studio.lingrui.studyagent.shared.api;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * 分页结果。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PageResult<T> {

    private List<T> items;
    private long total;
    private int page;
    private int size;

    public static <T> PageResult<T> of(Page<T> page) {
        return new PageResult<>(page.getContent(), page.getTotalElements(),
                page.getNumber() + 1, page.getSize());
    }

    public static <S, T> PageResult<T> of(Page<S> page, Function<S, T> mapper) {
        List<T> mapped = page.getContent().stream().map(mapper).toList();
        return new PageResult<>(mapped, page.getTotalElements(), page.getNumber() + 1, page.getSize());
    }

    public static <T> PageResult<T> empty(int page, int size) {
        return new PageResult<>(List.of(), 0, page, size);
    }
}
