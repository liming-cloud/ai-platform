declare module 'node:fs/promises' {
  export interface FileHandle {
    close(): Promise<void>;
  }

  export function mkdir(path: string, options: { recursive: true }): Promise<string | undefined>;
  export function open(path: string, flags: 'wx'): Promise<FileHandle>;
  export function readFile(path: string, encoding: 'utf8'): Promise<string>;
  export function unlink(path: string): Promise<void>;
  export function writeFile(path: string, data: string, encoding: 'utf8'): Promise<void>;
}

declare module 'node:path' {
  export function join(...paths: string[]): string;
}

declare module 'node:child_process' {
  export function execFileSync(
    file: string,
    args: string[],
    options: { encoding: 'utf8'; maxBuffer: number },
  ): string;
}
