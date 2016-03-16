#include "loadSeq.h"

int main (int argc, char* argv[]) {
  loadSeq_t module;
  loadSeq_api_t api(&module);
  module.init();
  api.init_sim_data();
  FILE *f = NULL;
  module.set_dumpfile(f);
  while(!api.exit()) api.tick();
  if (f) fclose(f);
}
