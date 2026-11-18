window.process = {cwd: () => "/"};
window.fs = {existsSync: () => false};
window.path = {
    join: (...parts) => parts.join("/").replace(/\/+/g, "/"),
    dirname: (path) => {
        const index = path.lastIndexOf("/");
        return index <= 0 ? "/" : path.slice(0, index);
    },
    parse: () => ({root: "/"})
};
