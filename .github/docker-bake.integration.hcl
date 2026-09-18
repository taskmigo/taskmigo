group "integration" {
  targets = ["migration", "web", "worker", "client"]
}

target "_server" {
  context    = "server"
  dockerfile = "Dockerfile"
}

target "migration" {
  inherits = ["_server"]
  target   = "migration"
  tags     = ["taskmigo-migration:integration"]
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
