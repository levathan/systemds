
#ifndef __CUM_PROD_H
#define __CUM_PROD_H

#pragma once

using uint = unsigned int;
#include <cuda_runtime.h>

/**
 * Do a cumulative product over all columns of a matrix
 * @param g_idata   input data stored in device memory (of size rows x cols)
 * @param g_odata   output/temporary array stored in device memory (of size rows x cols)
 * @param g_tdata temporary accumulated block offsets
 * @param rows      number of rows in input matrix
 * @param cols      number of columns in input matrix
 * @param block_height number of rows processed per block
 */

/**
 * Cumulative product instantiation for double
 */
extern "C"
__global__ void cumulative_prod_up_sweep_d(double *g_idata, double* g_tdata, uint rows, uint cols,
    uint block_height)
{
	ProductOp<double> op;
	cumulative_scan_up_sweep<ProductOp<double>, double>(g_idata, g_tdata, rows, cols, block_height, op);
}

/**
 * Cumulative product instantiation for float
 */
extern "C"
__global__ void cumulative_prod_up_sweep_f(double *g_idata, double* g_tdata, uint rows, uint cols,
    uint block_height)
{
	ProductOp<double> op;
	cumulative_scan_up_sweep<ProductOp<double>, double>(g_idata, g_tdata, rows, cols, block_height, op);
}

/**
 * Cumulative product instantiation for double
 */
extern "C" __global__ void cumulative_prod_down_sweep_d(double *g_idata, double *g_odata, double* g_tdata, uint rows,
    uint cols, uint block_height)
{
	ProductOp<double> op;
	cumulative_scan_down_sweep<ProductOp<double>, ProdNeutralElement<double>, double>(g_idata, g_odata, g_tdata, rows, cols, block_height, op);
}

/**
 * Cumulative product instantiation for float
 */
extern "C" __global__ void cumulative_prod_down_sweep_f(float *g_idata, float *g_odata, float* g_tdata, uint rows,
    uint cols, uint block_height)
{
	ProductOp<float> op;
	cumulative_scan_down_sweep<ProductOp<float>, ProdNeutralElement<float>, float>(g_idata, g_odata, g_tdata, rows, cols, block_height, op);
}

#endif // __CUM_PROD_H
