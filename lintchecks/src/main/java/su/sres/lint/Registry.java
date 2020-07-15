package su.sres.lint;

import com.android.tools.lint.client.api.IssueRegistry;
import com.android.tools.lint.detector.api.ApiKt;
import com.android.tools.lint.detector.api.Issue;

import java.util.Arrays;
import java.util.List;

@SuppressWarnings("UnstableApiUsage")
public final class Registry extends IssueRegistry {

    @Override
    public List<Issue> getIssues() {
        return Arrays.asList(ShadowLogDetector.LOG_NOT_SHADOW,
                ShadowLogDetector.LOG_NOT_APP,
                ShadowLogDetector.INLINE_TAG);
    }

    @Override
    public int getApi() {
        return ApiKt.CURRENT_API;
    }
}