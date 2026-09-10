#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <wchar.h>

#define COPY_BUFFER_SIZE (1024 * 1024)

#pragma pack(push, 1)
typedef struct {
    char magic[16];
    uint64_t extractor_size;
    uint64_t archive_size;
} PayloadFooter;
#pragma pack(pop)

static const char PAYLOAD_MAGIC[16] = {
    'Y','U','K','I','N','O','_','P','O','R','T','_','V','1',0,0
};

static void show_error(const wchar_t *message) {
    MessageBoxW(NULL, message, L"Yukino Counter 便携版", MB_OK | MB_ICONERROR);
}

static BOOL copy_segment(HANDLE source, uint64_t offset, uint64_t length, const wchar_t *destination) {
    LARGE_INTEGER position;
    position.QuadPart = (LONGLONG) offset;
    if (!SetFilePointerEx(source, position, NULL, FILE_BEGIN)) {
        return FALSE;
    }

    HANDLE output = CreateFileW(destination, GENERIC_WRITE, 0, NULL, CREATE_ALWAYS,
                                FILE_ATTRIBUTE_NORMAL, NULL);
    if (output == INVALID_HANDLE_VALUE) {
        return FALSE;
    }

    BYTE *buffer = (BYTE *) HeapAlloc(GetProcessHeap(), 0, COPY_BUFFER_SIZE);
    if (buffer == NULL) {
        CloseHandle(output);
        return FALSE;
    }

    BOOL ok = TRUE;
    while (length > 0) {
        DWORD requested = length > COPY_BUFFER_SIZE ? COPY_BUFFER_SIZE : (DWORD) length;
        DWORD read_count = 0;
        DWORD written_count = 0;
        if (!ReadFile(source, buffer, requested, &read_count, NULL) || read_count != requested ||
            !WriteFile(output, buffer, read_count, &written_count, NULL) || written_count != read_count) {
            ok = FALSE;
            break;
        }
        length -= read_count;
    }

    HeapFree(GetProcessHeap(), 0, buffer);
    CloseHandle(output);
    return ok;
}

static BOOL remove_tree(const wchar_t *directory) {
    wchar_t pattern[MAX_PATH];
    WIN32_FIND_DATAW data;
    if (swprintf(pattern, MAX_PATH, L"%ls\\*", directory) < 0) {
        return FALSE;
    }

    HANDLE search = FindFirstFileW(pattern, &data);
    if (search != INVALID_HANDLE_VALUE) {
        do {
            if (wcscmp(data.cFileName, L".") == 0 || wcscmp(data.cFileName, L"..") == 0) {
                continue;
            }
            wchar_t child[MAX_PATH];
            if (swprintf(child, MAX_PATH, L"%ls\\%ls", directory, data.cFileName) < 0) {
                continue;
            }
            if (data.dwFileAttributes & FILE_ATTRIBUTE_DIRECTORY) {
                remove_tree(child);
            } else {
                SetFileAttributesW(child, FILE_ATTRIBUTE_NORMAL);
                DeleteFileW(child);
            }
        } while (FindNextFileW(search, &data));
        FindClose(search);
    }
    SetFileAttributesW(directory, FILE_ATTRIBUTE_NORMAL);
    return RemoveDirectoryW(directory);
}

static DWORD run_and_wait(wchar_t *command, const wchar_t *working_directory, DWORD creation_flags) {
    STARTUPINFOW startup;
    PROCESS_INFORMATION process;
    ZeroMemory(&startup, sizeof(startup));
    ZeroMemory(&process, sizeof(process));
    startup.cb = sizeof(startup);
    startup.dwFlags = STARTF_USESHOWWINDOW;
    startup.wShowWindow = SW_HIDE;

    if (!CreateProcessW(NULL, command, NULL, NULL, FALSE, creation_flags, NULL,
                        working_directory, &startup, &process)) {
        return (DWORD) -1;
    }

    WaitForSingleObject(process.hProcess, INFINITE);
    DWORD exit_code = (DWORD) -1;
    GetExitCodeProcess(process.hProcess, &exit_code);
    CloseHandle(process.hThread);
    CloseHandle(process.hProcess);
    return exit_code;
}

int WINAPI wWinMain(HINSTANCE instance, HINSTANCE previous, PWSTR command_line, int show_command) {
    (void) instance;
    (void) previous;
    (void) command_line;
    (void) show_command;

    wchar_t executable[MAX_PATH];
    if (GetModuleFileNameW(NULL, executable, MAX_PATH) == 0) {
        show_error(L"无法读取便携版程序路径。");
        return 1;
    }

    HANDLE source = CreateFileW(executable, GENERIC_READ, FILE_SHARE_READ, NULL, OPEN_EXISTING,
                                FILE_ATTRIBUTE_NORMAL, NULL);
    if (source == INVALID_HANDLE_VALUE) {
        show_error(L"无法打开便携版程序文件。");
        return 1;
    }

    LARGE_INTEGER file_size;
    PayloadFooter footer;
    DWORD footer_read = 0;
    LARGE_INTEGER footer_position;
    if (!GetFileSizeEx(source, &file_size) || file_size.QuadPart < (LONGLONG) sizeof(footer)) {
        CloseHandle(source);
        show_error(L"便携版程序文件不完整。");
        return 1;
    }
    footer_position.QuadPart = file_size.QuadPart - (LONGLONG) sizeof(footer);
    if (!SetFilePointerEx(source, footer_position, NULL, FILE_BEGIN) ||
        !ReadFile(source, &footer, sizeof(footer), &footer_read, NULL) ||
        footer_read != sizeof(footer) || memcmp(footer.magic, PAYLOAD_MAGIC, 16) != 0) {
        CloseHandle(source);
        show_error(L"便携版程序数据校验失败。");
        return 1;
    }

    uint64_t payload_size = footer.extractor_size + footer.archive_size + sizeof(footer);
    if (payload_size > (uint64_t) file_size.QuadPart) {
        CloseHandle(source);
        show_error(L"便携版程序数据长度无效。");
        return 1;
    }

    wchar_t temp_root[MAX_PATH];
    wchar_t work_dir[MAX_PATH];
    if (GetTempPathW(MAX_PATH, temp_root) == 0 ||
        swprintf(work_dir, MAX_PATH, L"%lsYukinoCounter-portable-%lu", temp_root, GetCurrentProcessId()) < 0 ||
        !CreateDirectoryW(work_dir, NULL)) {
        CloseHandle(source);
        show_error(L"无法创建临时运行目录。");
        return 1;
    }

    wchar_t extractor_path[MAX_PATH];
    wchar_t archive_path[MAX_PATH];
    swprintf(extractor_path, MAX_PATH, L"%ls\\7zr.exe", work_dir);
    swprintf(archive_path, MAX_PATH, L"%ls\\payload.7z", work_dir);

    uint64_t payload_offset = (uint64_t) file_size.QuadPart - payload_size;
    BOOL copied = copy_segment(source, payload_offset, footer.extractor_size, extractor_path) &&
                  copy_segment(source, payload_offset + footer.extractor_size,
                               footer.archive_size, archive_path);
    CloseHandle(source);
    if (!copied) {
        remove_tree(work_dir);
        show_error(L"无法释放便携版程序数据。");
        return 1;
    }

    wchar_t extract_dir[MAX_PATH];
    wchar_t extract_command[MAX_PATH * 3];
    swprintf(extract_dir, MAX_PATH, L"%ls\\app", work_dir);
    swprintf(extract_command, MAX_PATH * 3, L"\"%ls\" x -y -o\"%ls\" \"%ls\"",
             extractor_path, extract_dir, archive_path);
    DWORD extract_exit = run_and_wait(extract_command, work_dir, CREATE_NO_WINDOW);
    if (extract_exit != 0) {
        remove_tree(work_dir);
        show_error(L"便携版程序解压失败。");
        return 1;
    }

    DeleteFileW(archive_path);
    DeleteFileW(extractor_path);

    wchar_t app_dir[MAX_PATH];
    wchar_t app_path[MAX_PATH];
    wchar_t app_command[MAX_PATH * 2];
    swprintf(app_dir, MAX_PATH, L"%ls\\YukinoCounter", extract_dir);
    swprintf(app_path, MAX_PATH, L"%ls\\YukinoCounter.exe", app_dir);
    swprintf(app_command, MAX_PATH * 2, L"\"%ls\"", app_path);
    DWORD app_exit = run_and_wait(app_command, app_dir, 0);
    if (app_exit == (DWORD) -1) {
        remove_tree(work_dir);
        show_error(L"Yukino Counter 启动失败。");
        return 1;
    }

    remove_tree(work_dir);
    return (int) app_exit;
}
