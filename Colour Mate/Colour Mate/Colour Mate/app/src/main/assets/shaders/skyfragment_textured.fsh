uniform lowp samplerCube skycube;
varying highp vec3 pixelNormal;
void main ()
{
  lowp vec4 r;
  r = textureCube (skycube, pixelNormal);
  gl_FragColor = r;
}

