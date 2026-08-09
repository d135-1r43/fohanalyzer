# -----------------------------------------------------------------------------
# The FOHanalyzer site: static files on nginx.
#
# There is no build stage because there is nothing to build — no bundler, no
# transpiler, no node_modules. What is in the repository is what is served.
#
# The base is nginx-unprivileged: it runs as UID 101 rather than root and
# listens on 8080, so the container needs no capabilities and can run under a
# read-only root filesystem with tmpfs on /tmp and /var/cache/nginx.
#
#   docker build -t fohanalyzer-site .
#   docker run --rm -p 8080:8080 fohanalyzer-site
# -----------------------------------------------------------------------------

FROM nginxinc/nginx-unprivileged:1.29-alpine

LABEL org.opencontainers.image.title="fohanalyzer.com" \
      org.opencontainers.image.description="Website for FOHanalyzer, an open-source dual-channel RTA for front-of-house engineers" \
      org.opencontainers.image.source="https://github.com/d135-1r43/fohanalyzer-website" \
      org.opencontainers.image.url="https://fohanalyzer.com" \
      org.opencontainers.image.licenses="GPL-3.0-or-later"

COPY nginx.conf /etc/nginx/conf.d/default.conf

WORKDIR /usr/share/nginx/html
COPY index.html contribute.html 404.html robots.txt sitemap.xml ./
COPY assets/ ./assets/

EXPOSE 8080

# Hits the config's own health endpoint, so a broken server block fails the
# check rather than passing because the process happens to be alive.
HEALTHCHECK --interval=30s --timeout=3s --start-period=5s --retries=3 \
	CMD ["/bin/sh", "-c", "wget -qO- http://127.0.0.1:8080/healthz || exit 1"]
