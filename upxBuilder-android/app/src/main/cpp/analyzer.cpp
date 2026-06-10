// upxBuilder native code analyzer.
//
// A fast single-pass scanner that finds common problems in source code:
// unbalanced brackets, unterminated strings and block comments, very long
// lines, and TODO/FIXME markers. It runs on every keystroke, so it is written
// in C++ for speed and exposed to Kotlin through JNI as libupxanalyzer.so.
//
// Output format: one problem per line, "LINE|SEVERITY|MESSAGE" where SEVERITY
// is E (error), W (warning) or I (info). Kotlin's DiagnosticsEngine parses this
// and falls back to an equivalent pure-Kotlin analyzer if the library is absent.

#include <jni.h>

#include <cstddef>
#include <string>
#include <vector>

namespace {

struct Problem {
    int line;
    char severity;  // 'E', 'W', 'I'
    std::string message;
};

struct Opener {
    char ch;
    int line;
};

constexpr std::size_t kMaxLineLength = 150;

void checkLineLength(std::size_t length, int line, std::vector<Problem>& out) {
    if (length > kMaxLineLength) {
        out.push_back({line, 'W',
                       "Line is very long (" + std::to_string(length) +
                           " chars) - consider breaking it up."});
    }
}

void scanCommentForMarkers(const std::string& comment, int line, std::vector<Problem>& out) {
    if (comment.find("TODO") != std::string::npos) {
        out.push_back({line, 'I', "TODO found in comment."});
    }
    if (comment.find("FIXME") != std::string::npos) {
        out.push_back({line, 'W', "FIXME found in comment."});
    }
}

std::vector<Problem> analyze(const std::string& code, bool cStyleComments) {
    std::vector<Problem> problems;
    std::vector<Opener> openers;

    const std::size_t n = code.size();
    std::size_t i = 0;
    int line = 1;
    std::size_t lineLength = 0;

    auto newline = [&] {
        checkLineLength(lineLength, line, problems);
        ++line;
        lineLength = 0;
    };

    while (i < n) {
        const char c = code[i];
        ++lineLength;

        if (c == '\n') {
            --lineLength;
            newline();
            ++i;
            continue;
        }

        // Line comments: // for C-like languages, # otherwise (e.g. Python).
        const bool slashComment =
            cStyleComments && c == '/' && i + 1 < n && code[i + 1] == '/';
        const bool hashComment = !cStyleComments && c == '#';
        if (slashComment || hashComment) {
            std::size_t end = code.find('\n', i);
            if (end == std::string::npos) end = n;
            scanCommentForMarkers(code.substr(i, end - i), line, problems);
            lineLength += end - i - 1;
            i = end;
            continue;
        }

        // Block comments (C-like languages only).
        if (cStyleComments && c == '/' && i + 1 < n && code[i + 1] == '*') {
            const std::size_t close = code.find("*/", i + 2);
            if (close == std::string::npos) {
                problems.push_back(
                    {line, 'E', "Unterminated block comment ('/*' without '*/')."});
                break;
            }
            scanCommentForMarkers(code.substr(i, close - i), line, problems);
            for (std::size_t j = i; j < close + 2; ++j) {
                if (code[j] == '\n') newline();
            }
            i = close + 2;
            continue;
        }

        // Python triple-quoted strings: skip wholesale.
        if (!cStyleComments && (c == '"' || c == '\'') && i + 2 < n &&
            code[i + 1] == c && code[i + 2] == c) {
            const std::string triple(3, c);
            const std::size_t close = code.find(triple, i + 3);
            if (close == std::string::npos) {
                problems.push_back({line, 'E', "Unterminated triple-quoted string."});
                break;
            }
            for (std::size_t j = i; j < close + 3; ++j) {
                if (code[j] == '\n') newline();
            }
            i = close + 3;
            continue;
        }

        // String / char literals.
        if (c == '"' || c == '\'') {
            std::size_t j = i + 1;
            bool terminated = false;
            while (j < n) {
                if (code[j] == '\\') {
                    j += 2;
                } else if (code[j] == c) {
                    terminated = true;
                    ++j;
                    break;
                } else if (code[j] == '\n') {
                    break;
                } else {
                    ++j;
                }
            }
            if (!terminated) {
                problems.push_back({line, 'E', "Unterminated string literal."});
            }
            if (j > n) j = n;
            lineLength += j - i - 1;
            i = j;
            continue;
        }

        if (c == '(' || c == '[' || c == '{') {
            openers.push_back({c, line});
            ++i;
            continue;
        }

        if (c == ')' || c == ']' || c == '}') {
            const char expected = (c == ')') ? '(' : (c == ']') ? '[' : '{';
            if (openers.empty()) {
                problems.push_back(
                    {line, 'E', std::string("Unmatched closing '") + c + "'."});
            } else if (openers.back().ch != expected) {
                problems.push_back(
                    {line, 'E',
                     std::string("Mismatched bracket: expected closing for '") +
                         openers.back().ch + "' (opened at line " +
                         std::to_string(openers.back().line) + ") but found '" + c +
                         "'."});
                openers.pop_back();
            } else {
                openers.pop_back();
            }
            ++i;
            continue;
        }

        ++i;
    }

    checkLineLength(lineLength, line, problems);
    for (const Opener& o : openers) {
        problems.push_back({o.line, 'E',
                            std::string("Unclosed '") + o.ch +
                                "' - no matching closing bracket found."});
    }
    return problems;
}

std::string format(const std::vector<Problem>& problems) {
    std::string out;
    for (const Problem& p : problems) {
        out += std::to_string(p.line);
        out += '|';
        out += p.severity;
        out += '|';
        out += p.message;
        out += '\n';
    }
    return out;
}

}  // namespace

extern "C" JNIEXPORT jstring JNICALL
Java_com_upx_builder_editor_NativeAnalyzer_analyze(JNIEnv* env,
                                                   jobject /* this */,
                                                   jstring jCode,
                                                   jstring jLineComment) {
    const char* codeChars = env->GetStringUTFChars(jCode, nullptr);
    const char* commentChars = env->GetStringUTFChars(jLineComment, nullptr);

    const std::string code(codeChars ? codeChars : "");
    const std::string lineComment(commentChars ? commentChars : "//");

    env->ReleaseStringUTFChars(jCode, codeChars);
    env->ReleaseStringUTFChars(jLineComment, commentChars);

    const std::string result = format(analyze(code, lineComment == "//"));
    return env->NewStringUTF(result.c_str());
}
