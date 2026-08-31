group "integration" {
  targets = ["bootstrap", "web", "worker", "client"]
}

target "_server" {
  context    = "server"
  dockerfile = "Dockerfile"
}

target "bootstrap" {
  inherits = ["_server"]
  target   = "bootstrap"
  tags     = ["taskmigo-bootstrap:integration"]
}

target "web" {
  inherits = ["_server"]
  target   = "web"
  tags     = ["taskmigo-web:integration"]
}

target "worker" {
  inherits = ["_server"]
  target   = "worker"
  tags     = ["taskmigo-worker:integration"]
}

target "client" {
  context    = "client"
  dockerfile = "Dockerfile"
  tags       = ["taskmigo-client:integration"]
}
