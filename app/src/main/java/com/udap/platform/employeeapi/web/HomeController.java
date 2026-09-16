package com.udap.platform.employeeapi.web;

import com.udap.platform.employeeapi.model.Employee;
import com.udap.platform.employeeapi.repository.EmployeeRepository;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Serves the browser-facing landing page at {@code /}.
 *
 * <p>Rendered server-side as a single self-contained document so the page has
 * no external asset dependencies — it must render even when the cluster has no
 * egress to a CDN.
 */
@RestController
public class HomeController {

    private final EmployeeRepository repository;

    public HomeController(EmployeeRepository repository) {
        this.repository = repository;
    }

    @GetMapping(value = "/", produces = MediaType.TEXT_HTML_VALUE)
    public String home() {
        List<Employee> employees = repository.findAll();

        StringBuilder rows = new StringBuilder();
        for (Employee employee : employees) {
            rows.append("<tr><td>").append(employee.id()).append("</td><td>")
                    .append(escape(employee.name())).append("</td><td>")
                    .append(escape(employee.email())).append("</td><td>")
                    .append(escape(employee.department())).append("</td><td>")
                    .append(escape(employee.title())).append("</td></tr>");
        }

        return """
                <!DOCTYPE html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <title>Employee API — Rancher RKE2 Platform</title>
                  <style>
                    :root { color-scheme: dark; }
                    * { box-sizing: border-box; }
                    body {
                      margin: 0; padding: 3rem 1.5rem;
                      font-family: ui-sans-serif, system-ui, -apple-system, "Segoe UI", Roboto, sans-serif;
                      background: radial-gradient(circle at 20%% 0%%, #1b2a4a 0%%, #0b1220 55%%);
                      color: #e6edf7; min-height: 100vh;
                    }
                    .wrap { max-width: 960px; margin: 0 auto; }
                    .badge {
                      display: inline-block; padding: .3rem .75rem; border-radius: 999px;
                      background: rgba(56,189,248,.14); color: #7dd3fc;
                      font-size: .78rem; letter-spacing: .08em; text-transform: uppercase;
                      border: 1px solid rgba(56,189,248,.3);
                    }
                    h1 { font-size: 2.4rem; margin: 1rem 0 .4rem; letter-spacing: -.02em; }
                    p.lead { color: #9fb0c9; margin: 0 0 2.25rem; font-size: 1.05rem; }
                    .grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(210px, 1fr)); gap: 1rem; margin-bottom: 2.5rem; }
                    .card {
                      background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.09);
                      border-radius: 14px; padding: 1.1rem 1.25rem;
                    }
                    .card h3 { margin: 0 0 .35rem; font-size: .76rem; text-transform: uppercase;
                      letter-spacing: .09em; color: #8aa0bd; font-weight: 600; }
                    .card p { margin: 0; font-size: 1.02rem; color: #e6edf7; }
                    code { background: rgba(255,255,255,.08); padding: .15rem .45rem;
                      border-radius: 6px; font-size: .92em; }
                    table { width: 100%%; border-collapse: collapse; font-size: .95rem;
                      background: rgba(255,255,255,.03); border-radius: 14px; overflow: hidden; }
                    th, td { text-align: left; padding: .8rem 1rem; border-bottom: 1px solid rgba(255,255,255,.07); }
                    th { background: rgba(255,255,255,.05); font-size: .76rem; text-transform: uppercase;
                      letter-spacing: .08em; color: #8aa0bd; }
                    tr:last-child td { border-bottom: none; }
                    footer { margin-top: 2.5rem; color: #64748b; font-size: .85rem; }
                    a { color: #7dd3fc; }
                  </style>
                </head>
                <body>
                  <div class="wrap">
                    <span class="badge">Running on RKE2 &middot; Managed by Rancher</span>
                    <h1>Employee API</h1>
                    <p class="lead">
                      A Spring Boot service deployed by Helm onto a Rancher-managed RKE2
                      cluster, behind the NGINX ingress controller.
                    </p>

                    <div class="grid">
                      <div class="card"><h3>Health</h3><p><code>GET /health</code></p></div>
                      <div class="card"><h3>Employees</h3><p><code>GET /employees</code></p></div>
                      <div class="card"><h3>By id</h3><p><code>GET /employees/{id}</code></p></div>
                      <div class="card"><h3>Records</h3><p>%d employees loaded</p></div>
                    </div>

                    <table>
                      <thead>
                        <tr><th>ID</th><th>Name</th><th>Email</th><th>Department</th><th>Title</th></tr>
                      </thead>
                      <tbody>
                        %s
                      </tbody>
                    </table>

                    <footer>
                      Terraform &middot; Ansible &middot; RKE2 &middot; Rancher &middot; Helm &middot; GitHub Actions
                      &mdash; provisioned with <a href="https://udap.io">UDAP</a>.
                    </footer>
                  </div>
                </body>
                </html>
                """.formatted(employees.size(), rows.toString());
    }

    private static String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
