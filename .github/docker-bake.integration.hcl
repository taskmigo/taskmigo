group "integration" {
  targets = ["bootstrap", "web", "worker", "client"]
}

target "bootstrap" {
  context    = "server"
  dockerfile = "apps/bootstrap/Dockerfile"
  tags       = ["taskmigo-bootstrap:integration"]
}

target "web" {
  context    = "server"
  dockerfile = "apps/web/Dockerfile"
  tags       = ["taskmigo-web:integration"]
}

target "worker" {
  context    = "server"
  dockerfile = "apps/worker/Dockerfile"
  tags       = ["taskmigo-worker:integration"]
}

target "client" {
  context    = "client"
  dockerfile = "Dockerfile"
  tags       = ["taskmigo-client:integration"]
}
